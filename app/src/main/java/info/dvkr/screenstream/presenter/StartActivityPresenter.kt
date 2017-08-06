package info.dvkr.screenstream.presenter

import info.dvkr.screenstream.BuildConfig
import info.dvkr.screenstream.dagger.PersistentScope
import info.dvkr.screenstream.model.EventBus
import info.dvkr.screenstream.model.GlobalStatus
import info.dvkr.screenstream.ui.StartActivityView
import rx.Scheduler
import rx.subscriptions.CompositeSubscription
import javax.inject.Inject

@PersistentScope
class StartActivityPresenter @Inject internal constructor(private val eventScheduler: Scheduler,
                                                          private val eventBus: EventBus,
                                                          private val globalStatus: GlobalStatus) {
    private val TAG = "StartActivityPresenter"

    private val subscriptions = CompositeSubscription()
    private var startActivity: StartActivityView? = null

    init {
        if (BuildConfig.DEBUG_MODE) println(TAG + ": Thread [${Thread.currentThread().name}] Constructor")
    }

    fun attach(activity: StartActivityView) {
        if (BuildConfig.DEBUG_MODE) println(TAG + ": Thread [${Thread.currentThread().name}] Attach")

        startActivity?.let { detach() }
        startActivity = activity

        // Events from StartActivity
        subscriptions.add(startActivity?.fromEvent()?.observeOn(eventScheduler)?.subscribe { fromEvent ->
            if (BuildConfig.DEBUG_MODE) println(TAG + ": Thread [${Thread.currentThread().name}] fromEvent: $fromEvent")
            when (fromEvent) {
            // Relaying message to ForegroundServicePresenter
                is StartActivityView.FromEvent.CurrentInterfacesRequest -> {
                    eventBus.sendEvent(EventBus.GlobalEvent.CurrentInterfacesRequest())
                }

            // Sending message to StartActivity
                is StartActivityView.FromEvent.TryStartStream -> {
                    globalStatus.error = null
                    if (globalStatus.isStreamRunning) throw IllegalStateException("Stream already running")
                    startActivity?.toEvent(StartActivityView.ToEvent.TryToStart())
                }

            // Relaying message to ForegroundServicePresenter
                is StartActivityView.FromEvent.StopStream -> {
                    if (!globalStatus.isStreamRunning) throw IllegalStateException("Stream not running")
                    eventBus.sendEvent(EventBus.GlobalEvent.StopStream())
                }

            // Relaying message to ForegroundServicePresenter
                is StartActivityView.FromEvent.AppExit -> {
                    eventBus.sendEvent(EventBus.GlobalEvent.AppExit())
                }

            // Getting  Error
                is StartActivityView.FromEvent.Error -> {
                    globalStatus.error = fromEvent.error
                    startActivity?.toEvent(StartActivityView.ToEvent.Error(fromEvent.error))
                }

            // Getting current Error
                is StartActivityView.FromEvent.GetError -> {
                    startActivity?.toEvent(StartActivityView.ToEvent.Error(globalStatus.error))
                }

                else -> println(TAG + ": Thread [${Thread.currentThread().name}] fromEvent: $fromEvent WARRING: IGNORED")
            }
        })

        // Global events
        subscriptions.add(eventBus.getEvent().observeOn(eventScheduler)
                .filter {
                    it is EventBus.GlobalEvent.StreamStatus ||
                            it is EventBus.GlobalEvent.ResizeFactor ||
                            it is EventBus.GlobalEvent.EnablePin ||
                            it is EventBus.GlobalEvent.SetPin ||
                            it is EventBus.GlobalEvent.CurrentClients ||
                            it is EventBus.GlobalEvent.CurrentInterfaces ||
                            it is EventBus.GlobalEvent.TrafficPoint
                }.subscribe { globalEvent ->
            if (BuildConfig.DEBUG_MODE) println(TAG + ": Thread [${Thread.currentThread().name}] globalEvent: $globalEvent")
            when (globalEvent) {
            // From ImageGeneratorImpl
                is EventBus.GlobalEvent.StreamStatus -> {
                    startActivity?.toEvent(StartActivityView.ToEvent.StreamStartStop(globalStatus.isStreamRunning))
                }

            // From SettingsActivityPresenter
                is EventBus.GlobalEvent.ResizeFactor -> {
                    startActivity?.toEvent(StartActivityView.ToEvent.ResizeFactor(globalEvent.value))
                }

            // From SettingsActivityPresenter
                is EventBus.GlobalEvent.EnablePin -> {
                    startActivity?.toEvent(StartActivityView.ToEvent.EnablePin(globalEvent.value))
                }

            // From SettingsActivityPresenter
                is EventBus.GlobalEvent.SetPin -> {
                    startActivity?.toEvent(StartActivityView.ToEvent.SetPin(globalEvent.value))
                }

            // From HttpServerImpl
                is EventBus.GlobalEvent.CurrentClients -> {
                    startActivity?.toEvent(StartActivityView.ToEvent.CurrentClients(globalEvent.clientsList))
                }

            // From ForegroundServicePresenter
                is EventBus.GlobalEvent.CurrentInterfaces -> {
                    startActivity?.toEvent(StartActivityView.ToEvent.CurrentInterfaces(globalEvent.interfaceList))
                }

            // From HttpServerImpl
                is EventBus.GlobalEvent.TrafficPoint -> {
                    startActivity?.toEvent(StartActivityView.ToEvent.TrafficPoint(globalEvent.trafficPoint))
                }
            }
        })

        // Sending current stream status
        startActivity?.toEvent(StartActivityView.ToEvent.StreamRunning(globalStatus.isStreamRunning))

        // Requesting current clients
        eventBus.sendEvent(EventBus.GlobalEvent.CurrentClientsRequest())
    }

    fun detach() {
        if (BuildConfig.DEBUG_MODE) println(TAG + ": Thread [${Thread.currentThread().name}] Detach")
        subscriptions.clear()
        startActivity = null
    }
}