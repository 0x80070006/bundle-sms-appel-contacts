package app.people.contacts.data

/**
 * Bundle: how the Contacts tab lays out the list. [Classic]: Favorites, then everyone in A–Z
 * letter groups. [ByLabel]: Favorites, then one section per label in the user's order, then
 * the contacts in no label.
 */
enum class ListLayout { Classic, ByLabel }

/**
 * Label names in the user's order: the names in [saved] that still exist, in that order, then
 * any label not ordered yet (new ones) alphabetically. Labels with the same name in different
 * accounts count as one, as they show as one section.
 */
fun orderLabelTitles(titles: Collection<String>, saved: List<String>): List<String> {
    val existing = titles.toSet()
    val ordered = saved.filter { it in existing }.distinct()
    val rest = existing.filter { it !in ordered }.sortedBy { it.lowercase() }
    return ordered + rest
}

/** [order] with [title] moved [by] places (negative: up), kept inside the list. */
fun moveLabelTitle(order: List<String>, title: String, by: Int): List<String> {
    val from = order.indexOf(title)
    if (from < 0) return order
    val to = (from + by).coerceIn(0, order.lastIndex)
    if (to == from) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * The sections of the list by label: (label name, its contacts in list order) for each label
 * that has someone in [contacts], in [order]; then (null, everyone in no label).
 */
fun sectionsByLabel(
    contacts: List<ContactSummary>,
    labels: List<Label>,
    order: List<String>,
): List<Pair<String?, List<ContactSummary>>> {
    val membersByTitle = labels.groupBy { it.title }.mapValues { (_, same) -> same.flatMapTo(HashSet()) { it.memberIds } }
    val labelled = HashSet<Long>()
    val sections = ArrayList<Pair<String?, List<ContactSummary>>>()
    orderLabelTitles(membersByTitle.keys, order).forEach { title ->
        val ids = membersByTitle[title].orEmpty()
        val members = contacts.filter { it.id in ids }
        if (members.isNotEmpty()) {
            sections += title to members
            members.forEach { labelled += it.id }
        }
    }
    val unlabelled = contacts.filter { it.id !in labelled }
    if (unlabelled.isNotEmpty()) sections += null to unlabelled
    return sections
}
