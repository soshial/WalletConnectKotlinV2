package com.walletconnect.web3.inbox.push.request

import com.walletconnect.push.client.Push
import com.walletconnect.push.client.PushWalletInterface
import com.walletconnect.web3.inbox.common.proxy.PushProxyInteractor
import com.walletconnect.web3.inbox.json_rpc.Web3InboxParams
import com.walletconnect.web3.inbox.json_rpc.Web3InboxRPC

internal class RejectRequestUseCase(
    private val pushWalletClient: PushWalletInterface,
    proxyInteractor: PushProxyInteractor,
) : PushRequestUseCase<Web3InboxParams.Request.Push.RejectParams>(proxyInteractor) {

    override fun invoke(rpc: Web3InboxRPC, params: Web3InboxParams.Request.Push.RejectParams) {
        pushWalletClient.reject(
            Push.Params.Reject(params.id, params.reason),
            onSuccess = { respondWithVoid(rpc) },
            onError = { error -> respondWithError(rpc, error) }
        )
    }
}