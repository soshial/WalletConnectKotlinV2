@file:JvmSynthetic

package com.walletconnect.android.relay

import android.content.SharedPreferences
import androidx.core.content.edit
import com.walletconnect.android.BuildConfig
import com.walletconnect.android.echo.EchoInterface
import com.walletconnect.android.internal.common.connection.ConnectivityState
import com.walletconnect.android.internal.common.di.AndroidCommonDITags
import com.walletconnect.android.internal.common.di.coreAndroidNetworkModule
import com.walletconnect.android.internal.common.exception.WRONG_CONNECTION_TYPE
import com.walletconnect.android.internal.common.scope
import com.walletconnect.android.internal.common.wcKoinApp
import com.walletconnect.android.utils.*
import com.walletconnect.foundation.crypto.data.repository.JwtRepository
import com.walletconnect.foundation.network.BaseRelayClient
import com.walletconnect.foundation.network.data.ConnectionController
import com.walletconnect.foundation.network.model.Relay
import kotlinx.coroutines.flow.*
import org.koin.core.qualifier.named

object RelayClient : BaseRelayClient(), RelayConnectionInterface {
    private val connectionController: ConnectionController by lazy { wcKoinApp.koin.get(named(AndroidCommonDITags.CONNECTION_CONTROLLER)) }
    private val networkState: ConnectivityState by lazy { wcKoinApp.koin.get(named(AndroidCommonDITags.CONNECTIVITY_STATE)) }
    private val isNetworkAvailable: StateFlow<Boolean> by lazy { networkState.isAvailable }
    private val isWSSConnectionOpened: MutableStateFlow<Boolean> = MutableStateFlow(false)

    @JvmSynthetic
    internal fun initialize(relayServerUrl: String, connectionType: ConnectionType, onError: (Throwable) -> Unit) {
        require(relayServerUrl.isValidRelayServerUrl()) { "Check the schema and projectId parameter of the Server Url" }

        logger = wcKoinApp.koin.get(named(AndroidCommonDITags.LOGGER))
        val jwtRepository = wcKoinApp.koin.get<JwtRepository>()
        val jwt = jwtRepository.generateJWT(relayServerUrl.strippedUrl()) { issuer ->
            val clientId = issuer.split(":").last()
            wcKoinApp.koin.get<SharedPreferences>().edit {
                putString(EchoInterface.KEY_CLIENT_ID, clientId)
            }
        }
        val serverUrl = relayServerUrl.addUserAgent(BuildConfig.SDK_VERSION)

        wcKoinApp.modules(coreAndroidNetworkModule(serverUrl, jwt, connectionType.toCommonConnectionType(), BuildConfig.SDK_VERSION))
        relayService = wcKoinApp.koin.get(named(AndroidCommonDITags.RELAY_SERVICE))

        collectConnectionErrors(onError)
    }

    private fun collectConnectionErrors(onError: (Throwable) -> Unit) {
        eventsFlow
            .onEach { event: Relay.Model.Event ->
                logger.log("kobe; $event")
                setIsWSSConnectionOpened(event)
            }
            .filterIsInstance<Relay.Model.Event.OnConnectionFailed>()
            .map { error -> error.throwable.toWalletConnectException }
            .onEach { walletConnectException -> onError(walletConnectException) }.launchIn(scope)
    }

    override val isConnectionAvailable: StateFlow<Boolean> by lazy {
        combine(isWSSConnectionOpened, isNetworkAvailable) { wss, internet -> wss && internet }
            .stateIn(scope, SharingStarted.Eagerly, false)
    }

    override fun connect(onError: (String) -> Unit) {
        println("kobe; connect")
        when (connectionController) {
            is ConnectionController.Automatic -> onError(WRONG_CONNECTION_TYPE)
            is ConnectionController.Manual -> {
                println("kobe; connect manual")
                (connectionController as ConnectionController.Manual).connect()
            }
        }
    }

    override fun disconnect(onError: (String) -> Unit) {
        when (connectionController) {
            is ConnectionController.Automatic -> onError(WRONG_CONNECTION_TYPE)
            is ConnectionController.Manual -> (connectionController as ConnectionController.Manual).disconnect()
        }
    }

    private fun setIsWSSConnectionOpened(event: Relay.Model.Event) {
        if (event is Relay.Model.Event.OnConnectionOpened<*>) {
            isWSSConnectionOpened.compareAndSet(expect = false, update = true)
        } else if (event is Relay.Model.Event.OnConnectionClosed || event is Relay.Model.Event.OnConnectionFailed) {
            isWSSConnectionOpened.compareAndSet(expect = true, update = false)
        }
    }
}