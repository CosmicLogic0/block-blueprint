package dev.buildblueprint.client;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

/**
 * Translations: every text the mod shows goes through t(). It follows the game's language (Options > Language).
 * The files are assets/buildblueprint/lang/bb_<language>.txt: one line per text, "English<TAB>translation".
 * Texts with numbers or names in them use {0}, {1}... ("Layer {0} of {1}"); the parts that go into them are
 * translated too when they are known texts. Anything without a translation stays English.
 */
public final class Tr {
    private Tr() {}

    private static final String[] LANGS = {"es", "pt", "de", "fr", "ru", "zh"};
    private static String loaded = "en";
    private static Map<String, String> exact = new HashMap<>();
    private static List<Object[]> templates = new ArrayList<>();   // {Pattern english, String translation}
    private static final Map<String, String> CACHE = new HashMap<>();

    /** The game's language as one of ours ("es", "de"...), or "en". */
    public static String lang() {
        try {
            String c = Minecraft.getInstance().options.languageCode;
            if (c == null) return "en";
            String p = c.toLowerCase(java.util.Locale.ROOT);
            int u = p.indexOf('_');
            if (u > 0) p = p.substring(0, u);
            for (String l : LANGS) if (l.equals(p)) return l;
        } catch (Throwable ignored) {}
        return "en";
    }

    public static String t(String s) {
        if (s == null || s.isEmpty()) return s;
        String code = lang();
        if (code.equals("en")) return s;
        if (!code.equals(loaded)) load(code);
        String c = CACHE.get(s);
        if (c != null) return c;
        String out = translate(s);
        if (CACHE.size() > 6000) CACHE.clear();
        CACHE.put(s, out);
        return out;
    }

    private static String translate(String s) { return translate(s, 0); }

    private static String translate(String s, int depth) {
        String e = exact.get(s);
        if (e != null) return e;
        for (Object[] tp : templates) {
            Matcher m = ((Pattern) tp[0]).matcher(s);
            if (!m.matches()) continue;
            String r = (String) tp[1];
            for (int i = m.groupCount(); i >= 1; i--) {
                String part = m.group(i);   // the parts are translated too: "Build complete! ... · {0} blocks a minute"
                r = r.replace("{" + (i - 1) + "}", depth < 3 && part.length() < s.length() ? translate(part, depth + 1) : part);
            }
            return r;
        }
        return s;
    }

    private static synchronized void load(String code) {
        Map<String, String> ex = new HashMap<>();
        List<Object[]> tps = new ArrayList<>();
        try (InputStream in = Tr.class.getResourceAsStream("/assets/buildblueprint/lang/bb_" + code + ".txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) {
                    int tab = line.indexOf('\t');
                    if (tab <= 0 || line.startsWith("#")) continue;
                    String en = line.substring(0, tab), tr = line.substring(tab + 1);
                    if (tr.isEmpty()) continue;
                    if (en.contains("{0}")) tps.add(new Object[]{pattern(en), tr}); else ex.put(en, tr);
                }
            }
        } catch (Exception ignored) {}
        // longer templates first, so "Layer {0} of {1}" wins over "{0} of {1}"
        tps.sort((a, b) -> Integer.compare(((Pattern) b[0]).pattern().length(), ((Pattern) a[0]).pattern().length()));
        exact = ex; templates = tps; loaded = code;
        CACHE.clear();
    }

    private static Pattern pattern(String en) {
        StringBuilder re = new StringBuilder();
        Matcher m = Pattern.compile("\\{\\d+\\}").matcher(en);
        int last = 0;
        while (m.find()) { re.append(Pattern.quote(en.substring(last, m.start()))).append("(.+?)"); last = m.end(); }
        re.append(Pattern.quote(en.substring(last)));
        return Pattern.compile(re.toString(), Pattern.DOTALL);
    }
}
