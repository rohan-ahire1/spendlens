package com.rohan.spend.data

/** Where a category's money goes when we total things up. */
enum class Bucket { LIVING, FAMILY, INVESTING, MOVE, ONE_OFF, INCOME, OWN }

enum class Cat(val label: String, val bucket: Bucket) {
    GROCERIES("Groceries", Bucket.LIVING),
    FOOD_DELIVERY("Food delivery", Bucket.LIVING),
    EATING_OUT("Eating out", Bucket.LIVING),
    COMMUTE("Commute", Bucket.LIVING),
    FUEL("Fuel", Bucket.LIVING),
    SUBSCRIPTIONS("Subscriptions", Bucket.LIVING),
    SHOPPING("Shopping", Bucket.LIVING),
    BILLS("Bills", Bucket.LIVING),
    MEDICAL("Medical", Bucket.LIVING),
    MOVIES("Movies", Bucket.LIVING),
    TRAVEL("Travel", Bucket.LIVING),
    SALON("Salon", Bucket.LIVING),
    CASH("Cash", Bucket.LIVING),
    PEOPLE("People", Bucket.LIVING),
    SMALL("Small payments", Bucket.LIVING),
    FAMILY("Family", Bucket.FAMILY),
    INVESTING("Investing", Bucket.INVESTING),
    CARD_BILL("Card bill", Bucket.MOVE),
    ONE_OFF("One-off", Bucket.ONE_OFF),
    SALARY("Salary", Bucket.INCOME),
    REFUND("Refund", Bucket.INCOME),
    RECEIVED("Money in", Bucket.INCOME),
    TRANSFER("Own transfer", Bucket.OWN);

    companion object {
        val spendable = entries.filter { it.bucket != Bucket.INCOME }
    }
}

object Categorizer {
    private val rules: List<Pair<Regex, Cat>> = listOf(
        "cred ?club|credit ?card|card ?bill|cc ?payment|hdfc ?bank ?digital ?cr|amazon ?pay ?credit" to Cat.CARD_BILL,
        "\\b(?:mom|mother|maa|mummy|papa|dad|father|aai|baba)\\b" to Cat.FAMILY,
        "zepto|blinkit|grofers|bigbasket|instamart|dmart|jiomart|swiggy ?instamart|more retail|reliance fresh|nature.?s basket|milkbasket|country delight" to Cat.GROCERIES,
        "swiggy|zomato|eatsure|box8|faasos" to Cat.FOOD_DELIVERY,
        "cafe|caf[eé]|coffee|restaurant|hotel|bakery|starbucks|mcdonald|domino|kfc|pizza|burger|chai|\\btea\\b|dhaba|bar\\b|brew|biryani|kitchen|foods?\\b" to Cat.EATING_OUT,
        "petrol|fuel|indian ?oil|iocl|hpcl|bpcl|\\bshell\\b|nayara|filling ?station|service ?station" to Cat.FUEL,
        "metro|uber|ola\\b|olacabs|rapido|irctc|redbus|fastag|parking|namma yatri|blusmart" to Cat.COMMUTE,
        "google ?play|netflix|spotify|youtube|prime ?video|amazon ?prime|hotstar|jiocinema|apple\\.com|itunes|meta|facebook|openai|chatgpt|anthropic|claude|notion|github|linkedin|sonyliv|zee5|audible|icloud" to Cat.SUBSCRIPTIONS,
        "electricity|bescom|msedcl|mahadiscom|tata ?power|adani|gas\\b|mahanagar|broadband|fibernet|act ?fiber|airtel|jio|vodafone|\\bvi\\b|tata ?play|recharge|bill ?desk|billdesk|insurance|lic\\b" to Cat.BILLS,
        "amazon|flipkart|myntra|ajio|nykaa|meesho|croma|decathlon|ikea|reliance digital|tata ?cliq|uniqlo|zara|h&m|lenskart" to Cat.SHOPPING,
        "pharm|apollo|medplus|hospital|clinic|1mg|tata ?1mg|netmeds|diagnostic|\\blabs?\\b|dental|doctor|health" to Cat.MEDICAL,
        "pvr|inox|bookmyshow|cinepolis|district" to Cat.MOVIES,
        "makemytrip|goibibo|indigo|interglobe|air ?india|akasa|vistara|oyo|airbnb|cleartrip|ixigo|easemytrip|booking\\.com" to Cat.TRAVEL,
        "salon|barber|haircut|spa\\b|looks|naturals|urban ?company" to Cat.SALON,
        "zerodha|groww|upstox|angel ?one|angelone|kuvera|coin|mutual ?fund|\\bmf\\b|nps|ppf|indmoney|smallcase|dhan\\b" to Cat.INVESTING,
    ).map { (p, c) -> Regex(p, RegexOption.IGNORE_CASE) to c }

    private val salaryRe = Regex("""salary|\bsal\b|payroll""", RegexOption.IGNORE_CASE)
    private val refundRe = Regex("""refund|reversed|reversal|cashback""", RegexOption.IGNORE_CASE)
    private val neftRe = Regex("NEFT", RegexOption.IGNORE_CASE)

    fun auto(t: Txn): Cat {
        if (!t.debit) {
            return when {
                refundRe.containsMatchIn(t.body) -> Cat.REFUND
                salaryRe.containsMatchIn(t.body) -> Cat.SALARY
                // Big NEFT credits from a company are almost always salary.
                neftRe.containsMatchIn(t.body) && t.amount >= 25_000_00 -> Cat.SALARY
                else -> Cat.RECEIVED
            }
        }
        if (t.channel == Channel.ATM) return Cat.CASH
        val hay = t.payee + " " + t.payeeKey
        rules.firstOrNull { it.first.containsMatchIn(hay) }?.let { return it.second }
        return if (t.amount >= 1_000_00) Cat.PEOPLE else Cat.SMALL
    }
}
