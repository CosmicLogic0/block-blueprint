package dev.buildblueprint.core;

/**
 * Collects the pasted "/buildblueprint load &lt;id&gt;.&lt;part&gt;.&lt;total&gt; &lt;data&gt;" lines (any order, repeats ok)
 * and decodes the blueprint once every part has arrived.
 */
public final class ChunkAssembler {
    public record Progress(int have, int total, Blueprint done, String data) {
        public Progress(int have, int total, Blueprint done) { this(have, total, done, null); }
    }

    private String id;
    private String[] parts;
    private int have;

    public synchronized Progress accept(String token, String data) throws BlueprintException {
        String[] t = token.split("\\.");
        if (t.length != 3) throw new BlueprintException("That line isn't in the right format. Copy it again from the app.");
        int index, total;
        try {
            index = Integer.parseInt(t[1]);
            total = Integer.parseInt(t[2]);
        } catch (NumberFormatException e) {
            throw new BlueprintException("That line isn't in the right format. Copy it again from the app.");
        }
        if (total < 1 || total > 5000 || index < 1 || index > total) throw new BlueprintException("That line's part number is invalid.");

        if (id == null || !id.equals(t[0]) || parts == null || parts.length != total) { // a different blueprint: start over
            id = t[0]; parts = new String[total]; have = 0;
        }
        if (parts[index - 1] == null) have++;
        parts[index - 1] = data;

        if (have < total) return new Progress(have, total, null);
        String joined = String.join("", parts);
        reset();
        return new Progress(total, total, BlueprintCodec.decode(joined), joined);
    }

    public synchronized void reset() { id = null; parts = null; have = 0; }

    public synchronized boolean pending() { return parts != null; }

    /** Which part numbers (1-based) are still missing. */
    public synchronized String missing() {
        if (parts == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) if (parts[i] == null) { if (sb.length() > 0) sb.append(", "); sb.append(i + 1); }
        return sb.toString();
    }
}
