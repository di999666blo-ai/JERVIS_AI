package ai.diablo.bobby

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class BobbyTraderSkill(
    val id: String,
    val title: String,
    val purpose: String,
    val instruction: String
)

private val bobbyTraderSkills = listOf(
    BobbyTraderSkill("copycat", "COPYCAT • INVESTOR FILINGS", "Rebuild a public investor's disclosed US equity holdings from SEC 13F filings and compare quarters.", """
Find the investor/fund's latest original SEC 13F-HR filing and prior-quarter filing. Report holdings, weights, additions, increases, trims and exits; identify filing date and portfolio as-of date. Match CUSIPs to tickers carefully and flag uncertain matches. Explain that 13F reports are delayed snapshots, long US-listed positions only, and omit shorts, cash, bonds, foreign/private holdings and intra-quarter trades. Never imply the investor personally made a trade if the filing is by a fund. Do not recommend blindly copying.
""".trimIndent()),
    BobbyTraderSkill("news", "NEWS • HOLDINGS MONITOR", "Review recent company headlines and SEC filings for a watchlist or portfolio.", """
For each ticker, gather recent headlines and primary SEC filings (especially material 8-Ks). Separate confirmed filing facts from headline claims, show publication timestamps and direct source links, flag stale/paywalled/unverified items, and explain which holding each item touches. Do not predict price direction or trade on headlines. If live source access is unavailable, say so and ask for source material rather than inventing news.
""".trimIndent()),
    BobbyTraderSkill("budget", "BUDGET • POSITION SIZING", "Convert target portfolio weights into proposed position sizes with a cash buffer and concentration caps.", """
Ask for account currency, total investable budget, cash buffer, per-position cap, minimum order size, fractional-share availability and target weights if missing. Calculate proposed allocations, residual cash and rounding differences transparently. Show assumptions and formulas. Create proposals only; do not send orders. Never assume USD or fractional shares without confirmation.
""".trimIndent()),
    BobbyTraderSkill("pelosi", "CONGRESS • PUBLIC TRADE DISCLOSURES", "Read public House/Senate transaction disclosures and flag filing delays.", """
Use only official House Clerk or Senate disclosure documents. Report filer, owner (member/spouse/dependent), asset, buy/sell, reported amount range, transaction date, filing date and source document. Explain that disclosures can arrive days or weeks after the trade and are not real-time signals or inside information. Preserve amount ranges; do not convert them into invented exact values.
""".trimIndent()),
    BobbyTraderSkill("analyst", "ANALYST • COMPANY FUNDAMENTALS", "Analyse reported company earnings, margins, growth and balance-sheet facts.", """
Prioritise company SEC filings and reported statements. Compare the latest quarter with the same quarter a year earlier; show revenue, net income, diluted EPS, margins and trailing-twelve-month context where supported. Mark derived values, one-offs, missing facts and data lags. Separate reported facts from estimates and interpretation. Do not label a stock buy/sell or invent valuation inputs.
""".trimIndent()),
    BobbyTraderSkill("screener", "SCREENER • RULE-BASED STOCK FILTER", "Rank companies against explicit fundamental or market criteria.", """
First obtain the user's exact screening rules, universe, market, timeframe and data requirements. For each candidate, show pass/fail per rule, source, as-of date and missing data. Rank only using the stated criteria; do not silently change thresholds or fill gaps with guesses. Screening is not a personalised recommendation.
""".trimIndent()),
    BobbyTraderSkill("risk", "RISK • PRE-TRADE CHECK", "Stress-check a proposed order against user-defined risk limits.", """
Require account equity, risk budget, entry, stop, instrument contract specifications, leverage/multiplier, fees/slippage assumptions, open exposure and daily loss limit. Calculate position risk only when inputs are sufficient. Flag missing/uncertain contract values and reject orders that exceed the user's limits. This is a checklist/calculation only; it cannot authorise or place a trade.
""".trimIndent()),
    BobbyTraderSkill("broker", "BROKER • ORDER TICKET REVIEW", "Prepare a human-readable order ticket for manual review.", """
Translate a user-approved proposal into a review-only ticket: instrument, side, size, order type, limit/stop, time-in-force, estimated exposure, risk check result and data timestamp. Require explicit confirmation of every field before any future broker connector could be used. This BOBBY integration has no broker order-placement adapter: never claim an order was sent or filled.
""".trimIndent()),
    BobbyTraderSkill("tax", "TAX • COST BASIS & WASH-SALE CHECK", "Organise cost-basis lots and estimate realised/unrealised gains.", """
Ask for actual lot dates, share counts, total cost basis, current prices and relevant planned trades. Show FIFO/HIFO/specific-lot calculations and potential US wash-sale windows only when applicable. State missing accounts or transactions may change results. This is arithmetic and educational information, not tax advice; recommend a qualified tax professional for decisions.
""".trimIndent()),
    BobbyTraderSkill("alerts", "ALERTS • PRICE & LEVEL WATCH", "Define price, stop, target and market-session alerts for a watchlist.", """
Clarify ticker/instrument, venue, trigger direction, price level, timeframe, session and expiry. Explain that this BOBBY skill pack does not run a background market-data monitor yet. It can document alert rules and analyse user-supplied/currently sourced quotes, but must not claim continuous monitoring or an alert has been scheduled unless a real scheduler/data feed confirms it.
""".trimIndent()),
    BobbyTraderSkill("rebalance", "REBALANCE • PORTFOLIO DRIFT", "Compare current portfolio weights with target weights and calculate proposed adjustments.", """
Require current holdings, quantities, prices, target weights, cash, account currency and drift bands. Calculate current weights, deviations and proposed buy/sell quantities; show fees/tax implications as unknown unless supplied. Mark all adjustments as proposals only. Never place trades or assume a broker account is connected.
""".trimIndent())
)

@Composable
fun BobbyTraderSkillPackDialog(onDismiss: () -> Unit, onRunSkill: (String) -> Unit) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf("analyst") }
    val selected = bobbyTraderSkills.first { it.id == selectedId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("BOBBY TRADING PLACE • 11 SKILLS", color = Color(0xFFFF1744)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Adapted as an Android-native, AI-guided skill pack from Jakeschincariol/trader-skill (MIT). Choose a workflow and BOBBY will run its analysis through your configured AI chat.", style = MaterialTheme.typography.bodySmall)
                Text("INCLUDED WORKFLOWS", color = Color(0xFFFF1744))
                bobbyTraderSkills.forEach { skill ->
                    val active = skill.id == selectedId
                    OutlinedButton(
                        onClick = { selectedId = skill.id },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text((if (active) "● " else "○ ") + skill.title, color = if (active) Color(0xFFFF1744) else Color.White)
                            Text(skill.purpose, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Column(
                    Modifier.fillMaxWidth().background(Color(0xFF171017), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text("SELECTED: ${selected.title}", color = Color(0xFFFF1744))
                    Text(selected.purpose, color = Color.White)
                    Text("WORKFLOW RULES", color = Color(0xFFFF1744), style = MaterialTheme.typography.labelMedium)
                    Text(selected.instruction, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        onRunSkill("""
BOBBY TRADER SKILL: ${selected.title}
Goal: ${selected.purpose}

Follow this workflow:
${selected.instruction}

Operating rules:
- Use fresh, verifiable sources when available and state the as-of time.
- Clearly separate sourced facts, calculations, assumptions and unknowns.
- Ask only for essential missing inputs; never fabricate prices, filings, balances or results.
- This is analysis/planning only. No broker is connected through this skill pack and no trade may be placed.
- Present the result with a concise summary, calculations/table where useful, risks, sources and what remains unverified.
""".trimIndent())
                    }, modifier = Modifier.fillMaxWidth()) { Text("RUN SELECTED SKILL WITH BOBBY") }
                }
                Text("LIMITATIONS: This adds the 11 workflows and their guardrails, not the upstream Python command-line runtime. Continuous price alerts, SEC data execution and broker integration still need their own tested connectors. No autonomous/live order placement.", color = Color(0xFFFFB74D), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Jakeschincariol/trader-skill"))) }
                }) { Text("OPEN ORIGINAL GITHUB PROJECT", color = Color(0xFFFF1744)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE", color = Color(0xFFFF1744)) } }
    )
}
