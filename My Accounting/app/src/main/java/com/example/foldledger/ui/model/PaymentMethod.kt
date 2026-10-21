package com.example.foldledger.ui.model

/** 支付方式 */
data class PaymentMethod(val key: String, val label: String)

object PaymentMethods {
    val ALL: List<PaymentMethod> = listOf(
        PaymentMethod("cash", "现金"),
        PaymentMethod("wechat", "微信"),
        PaymentMethod("alipay", "支付宝"),
        PaymentMethod("bankcard", "银行卡"),
        PaymentMethod("other", "其他")
    )

    private val map = ALL.associateBy { it.key }
    fun labelOf(key: String): String = map[key]?.label ?: key

    /** 按 key 或显示名反查 key（导入 CSV 时把中文支付方式名还原为内部 key） */
    fun keyOfLabel(s: String): String? {
        if (s.isBlank()) return null
        map[s]?.let { return it.key }
        ALL.firstOrNull { it.label == s }?.let { return it.key }
        return null
    }
}
