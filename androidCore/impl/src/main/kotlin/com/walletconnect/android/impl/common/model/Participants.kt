package com.walletconnect.android.impl.common.model

import com.walletconnect.foundation.common.model.PublicKey

data class Participants(
    val senderPublicKey: PublicKey,
    val receiverPublicKey: PublicKey,
)