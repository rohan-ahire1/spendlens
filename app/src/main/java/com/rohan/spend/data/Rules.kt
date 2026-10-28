package com.rohan.spend.data

/** A payee rule with the name people know the payee by, and how many payments it files. */
data class RuleInfo(val key: String, val cat: Cat, val name: String, val count: Int)

/** Names come from the newest payment with that key; a rule with none left falls back to its key. */
fun ruleInfos(rules: Map<String, Cat>, txns: List<Txn>): List<RuleInfo> {
    val byKey = txns.filter { it.payeeKey in rules }.groupBy { it.payeeKey }
    return rules.map { (k, c) ->
        val l = byKey[k].orEmpty()
        RuleInfo(k, c, l.maxByOrNull { it.time }?.payee ?: k, l.size)
    }.sortedBy { it.name.lowercase() }
}
