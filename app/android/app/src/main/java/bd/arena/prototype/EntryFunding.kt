package bd.arena.prototype

internal data class EntryFunding(val deposit:Long,val winnings:Long)

internal fun entryFunding(fee:Long,deposit:Long,winnings:Long):EntryFunding {
    require(fee in 1..maxPaisa&&deposit in 0..maxPaisa&&winnings in 0..maxPaisa){"Wallet balance requires reconciliation."}
    val fromDeposit=minOf(fee,deposit)
    val fromWinnings=fee-fromDeposit
    require(fromWinnings<=winnings){"Not enough deposit and winnings balance for this entry."}
    return EntryFunding(fromDeposit,fromWinnings)
}
