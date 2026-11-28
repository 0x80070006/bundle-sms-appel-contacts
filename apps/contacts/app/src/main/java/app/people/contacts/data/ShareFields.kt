package app.people.contacts.data

/**
 * Bundle: "checked sharing". A contact card is shared field by field: the user ticks what is
 * sent, so a home address or a private number doesn't leave by mistake. Each [ShareField] is one
 * value of the card; [shareVCard] writes only the ticked ones.
 */
data class ShareField(val id: String, val kind: Kind, val label: String, val value: String, val defaultOn: Boolean) {
    enum class Kind { Phone, Email, Address, Company, Title, Birthday, Website, Note }
}

/** Every value of [contact] that can be shared. Addresses, notes and birthday start unticked. */
fun shareFieldsOf(contact: ContactDetails): List<ShareField> = buildList {
    contact.phones.forEachIndexed { i, p -> add(ShareField("p$i", ShareField.Kind.Phone, p.label, p.value, true)) }
    contact.emails.forEachIndexed { i, e -> add(ShareField("e$i", ShareField.Kind.Email, e.label, e.value, true)) }
    contact.company?.let { add(ShareField("org", ShareField.Kind.Company, "", it, true)) }
    contact.jobTitle?.let { add(ShareField("title", ShareField.Kind.Title, "", it, true)) }
    contact.websites.forEachIndexed { i, w -> add(ShareField("w$i", ShareField.Kind.Website, "", w, true)) }
    contact.addresses.forEachIndexed { i, a -> add(ShareField("a$i", ShareField.Kind.Address, a.label, a.value, false)) }
    contact.events.firstOrNull { it.type == 3 }?.let { add(ShareField("bday", ShareField.Kind.Birthday, "", it.value, false)) }
    contact.notes.forEachIndexed { i, n -> add(ShareField("n$i", ShareField.Kind.Note, "", n, false)) }
}

private fun esc(value: String) =
    value.replace("\\", "\\\\").replace(",", "\\,").replace(";", "\\;").replace("\n", "\\n")

/** A vCard 3.0 with the name and only the [chosen] fields. */
fun shareVCard(name: String, fields: List<ShareField>, chosen: Set<String>): String = buildString {
    appendLine("BEGIN:VCARD")
    appendLine("VERSION:3.0")
    appendLine("FN:${esc(name)}")
    appendLine("N:;${esc(name)};;;")
    fields.filter { it.id in chosen }.forEach { f ->
        when (f.kind) {
            ShareField.Kind.Phone -> appendLine("TEL:${esc(f.value)}")
            ShareField.Kind.Email -> appendLine("EMAIL:${esc(f.value)}")
            ShareField.Kind.Address -> appendLine("ADR:;;${esc(f.value)};;;;")
            ShareField.Kind.Company -> appendLine("ORG:${esc(f.value)}")
            ShareField.Kind.Title -> appendLine("TITLE:${esc(f.value)}")
            ShareField.Kind.Birthday -> appendLine("BDAY:${esc(f.value)}")
            ShareField.Kind.Website -> appendLine("URL:${esc(f.value)}")
            ShareField.Kind.Note -> appendLine("NOTE:${esc(f.value)}")
        }
    }
    append("END:VCARD")
}
