package ai.diablo.bobby

data class TradingRiskConfig(
    val demoOnly: Boolean = true,
    val maxStake: Double = 1.0,
    val dailyLossLimit: Double = 5.0,
    val emergencyStop: Boolean = true
)
data class TradingPermission(val allowed: Boolean, val reason: String)

/**
 * Local pre-trade guard only. This class does not connect to a broker or execute orders.
 * Real trading must remain disabled until a documented broker adapter and explicit consent flow exist.
 */
class BobbyTradingGuard(private val config: TradingRiskConfig) {
    fun authorizeDemoOrder(stake: Double, dailyNetProfit: Double, isDemoAccount: Boolean): TradingPermission {
        if (config.emergencyStop) return TradingPermission(false, "Emergency stop is active.")
        if (!config.demoOnly || !isDemoAccount) return TradingPermission(false, "Only demo-account simulation is enabled.")
        if (!stake.isFinite() || stake <= 0.0) return TradingPermission(false, "Stake must be a positive finite amount.")
        if (stake > config.maxStake) return TradingPermission(false, "Stake exceeds the configured maximum.")
        if (dailyNetProfit <= -kotlin.math.abs(config.dailyLossLimit)) return TradingPermission(false, "Daily loss limit reached.")
        return TradingPermission(true, "Demo-only risk checks passed. No live order is authorised.")
    }
}
