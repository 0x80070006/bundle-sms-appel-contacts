package bundle.core.sync

/** How two contacts that no sync has linked yet relate. */
enum class Match {
    /** The same person: they can be joined without asking. */
    Same,

    /** Probably the same person (a shared number or email, but different names): keep both and let the user decide. */
    Similar,

    None,
}

object Merge {
    /**
     * Compares two contacts that aren't linked:
     * - a shared number or email and **one side has no name** (only the number): the same person, the
     *   named one is kept;
     * - a shared number or email and the same name: the same person;
     * - a shared number or email and different names: [Match.Similar], both stay (Android and the
     *   app's merge screen then offer to merge them).
     */
    fun match(a: Fields, b: Fields): Match {
        val shared = a.keys("phones").intersect(b.keys("phones")).isNotEmpty() ||
            a.keys("emails").intersect(b.keys("emails")).isNotEmpty()
        if (!shared) return Match.None
        if (!a.hasName || !b.hasName) return Match.Same
        return if (a.nameKey == b.nameKey) Match.Same else Match.Similar
    }

    /**
     * The merge of a contact edited on two phones, given the version both started from ([base], null
     * when they were never linked).
     *
     * - A single value changed on one side only is taken from that side; changed on both, the more
     *   recent change wins, but a value is never replaced by nothing when the other side has one.
     * - A set (numbers, emails…) keeps what either side added, and drops what either side removed
     *   since [base].
     */
    fun threeWay(base: Fields?, local: Fields, remote: Fields, localAt: Long, remoteAt: Long): Fields {
        val newerIsRemote = remoteAt > localAt
        val scalars = HashMap<String, String>()
        (local.scalars.keys + remote.scalars.keys + base?.scalars?.keys.orEmpty()).forEach { name ->
            val l = local.scalar(name)
            val r = remote.scalar(name)
            val b = base?.scalar(name)
            val value = when {
                l == r -> l
                b != null && l == b -> r
                b != null && r == b -> l
                l.isEmpty() -> r
                r.isEmpty() -> l
                else -> if (newerIsRemote) r else l
            }
            if (value.isNotEmpty()) scalars[name] = value
        }
        val sets = HashMap<String, Map<String, Item>>()
        (local.sets.keys + remote.sets.keys).forEach { kind ->
            val l = local.sets[kind].orEmpty()
            val r = remote.sets[kind].orEmpty()
            val b = base?.sets?.get(kind)
            val merged = LinkedHashMap<String, Item>()
            (l.keys + r.keys).forEach { key ->
                val inL = key in l
                val inR = key in r
                val inB = b != null && key in b
                when {
                    inL && inR -> merged[key] = if (newerIsRemote) r.getValue(key) else l.getValue(key)
                    inL && !inB -> merged[key] = l.getValue(key)
                    inR && !inB -> merged[key] = r.getValue(key)
                    // In the base but missing on one side: that side removed it.
                }
            }
            if (merged.isNotEmpty()) sets[kind] = merged
        }
        return Fields(scalars, sets)
    }
}
