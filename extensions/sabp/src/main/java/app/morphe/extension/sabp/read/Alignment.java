package app.morphe.extension.sabp.read;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Maps audiobook time (seconds from the start of the book) to word positions in the attached text.
 *
 * <p>Built from anchors: exact ones from EPUB media overlays, automatic ones from speech
 * recognition, and manual "I'm hearing this" taps. Between anchors the mapping is linear, so even
 * a few anchors give a good follow-along; with none it falls back to a whole-book estimate.
 */
public final class Alignment {
    public static final int SRC_OVERLAY = 0, SRC_SPEECH = 1, SRC_MANUAL = 2;

    public static final class Anchor implements Comparable<Anchor> {
        public final double seconds;
        public final int word;
        public final int source;
        public final float confidence;

        public Anchor(double seconds, int word, int source, float confidence) {
            this.seconds = seconds;
            this.word = word;
            this.source = source;
            this.confidence = confidence;
        }

        @Override
        public int compareTo(Anchor o) {
            return Double.compare(seconds, o.seconds);
        }
    }

    private final ArrayList<Anchor> anchors = new ArrayList<>();
    private int words;
    private double bookSeconds;

    public synchronized void setBounds(int words, double bookSeconds) {
        this.words = Math.max(1, words);
        if (bookSeconds > 0) this.bookSeconds = bookSeconds;
    }

    public synchronized int anchorCount(int source) {
        int n = 0;
        for (Anchor a : anchors) if (source < 0 || a.source == source) n++;
        return n;
    }

    public synchronized boolean hasOverlay() {
        return anchorCount(SRC_OVERLAY) > 0;
    }

    /**
     * Adds an anchor and removes older automatic anchors that contradict it (time order and word
     * order must agree). Manual anchors win over speech anchors; overlay anchors are never removed.
     */
    public synchronized boolean add(Anchor a) {
        ArrayList<Anchor> drop = new ArrayList<>();
        for (Anchor o : anchors) {
            boolean conflict = (o.seconds < a.seconds && o.word > a.word) || (o.seconds > a.seconds && o.word < a.word)
                    || Math.abs(o.seconds - a.seconds) < 4;
            if (!conflict) continue;
            if (o.source == SRC_OVERLAY) return false;
            if (a.source == SRC_SPEECH && o.source == SRC_MANUAL && Math.abs(o.seconds - a.seconds) >= 4) return false;
            drop.add(o);
        }
        anchors.removeAll(drop);
        anchors.add(a);
        Collections.sort(anchors);
        return true;
    }

    public synchronized void clear(int source) {
        ArrayList<Anchor> keep = new ArrayList<>();
        for (Anchor a : anchors) if (source >= 0 && a.source != source) keep.add(a);
        anchors.clear();
        anchors.addAll(keep);
    }

    public synchronized void setOverlays(List<BookText.Overlay> overlays) {
        clear(SRC_OVERLAY);
        for (BookText.Overlay o : overlays) anchors.add(new Anchor(o.seconds, o.word, SRC_OVERLAY, 1f));
        Collections.sort(anchors);
    }

    private double rate() {
        return bookSeconds > 0 ? words / bookSeconds : 2.5; // ~150 words per minute
    }

    /** Word being spoken at a given book time. */
    public synchronized int wordAt(double t) {
        if (anchors.isEmpty()) return clamp((int) Math.round(t * rate()));
        Anchor first = anchors.get(0);
        if (t <= first.seconds) return clamp((int) Math.round(first.word - (first.seconds - t) * localRate(0)));
        Anchor last = anchors.get(anchors.size() - 1);
        if (t >= last.seconds) return clamp((int) Math.round(last.word + (t - last.seconds) * localRate(anchors.size() - 2)));
        int lo = 0, hi = anchors.size() - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) / 2;
            if (anchors.get(mid).seconds <= t) lo = mid;
            else hi = mid;
        }
        Anchor a = anchors.get(lo), b = anchors.get(hi);
        double f = (t - a.seconds) / Math.max(0.001, b.seconds - a.seconds);
        return clamp((int) Math.round(a.word + f * (b.word - a.word)));
    }

    /** Book time at which a word is spoken (inverse of wordAt). */
    public synchronized double timeOf(int w) {
        if (anchors.isEmpty()) return w / rate();
        Anchor first = anchors.get(0);
        if (w <= first.word) return Math.max(0, first.seconds - (first.word - w) / localRate(0));
        Anchor last = anchors.get(anchors.size() - 1);
        if (w >= last.word) return last.seconds + (w - last.word) / localRate(anchors.size() - 2);
        for (int i = 0; i + 1 < anchors.size(); i++) {
            Anchor a = anchors.get(i), b = anchors.get(i + 1);
            if (w >= a.word && w <= b.word) {
                double f = (w - a.word) / (double) Math.max(1, b.word - a.word);
                return a.seconds + f * (b.seconds - a.seconds);
            }
        }
        return w / rate();
    }

    /** Words per second around anchor i (falls back to the book average). */
    private double localRate(int i) {
        if (i >= 0 && i + 1 < anchors.size()) {
            Anchor a = anchors.get(i), b = anchors.get(i + 1);
            double dt = b.seconds - a.seconds;
            if (dt > 20 && b.word > a.word) {
                double r = (b.word - a.word) / dt;
                if (r > 0.8 && r < 6) return r;
            }
        }
        double r = rate();
        return r > 0.8 && r < 6 ? r : 2.5;
    }

    private int clamp(int w) {
        return Math.max(0, Math.min(words - 1, w));
    }

    // ---- voting matcher ---------------------------------------------------------------------

    /** Result of locating a transcript in the book. */
    public static final class Match {
        public final int word;
        public final float confidence;

        Match(int word, float confidence) {
            this.word = word;
            this.confidence = confidence;
        }
    }

    /**
     * Finds where a short transcript occurs in the book text. Every transcript word votes for the
     * start positions it is consistent with; rare words count more than common ones. Searching
     * the whole book is cheap this way, so a bad estimate can't hide the right spot.
     *
     * @param near     expected word position, or -1 for anywhere
     * @param radius   how far from near to look (ignored when near is -1)
     */
    public static Match locate(BookText text, List<String> transcript, int near, int radius) {
        if (transcript.size() < 4 || text.wordCount() == 0) return null;
        Map<String, int[]> pos = text.positions();
        int n = text.wordCount();
        int bucket = 4;
        java.util.HashMap<Integer, float[]> votes = new java.util.HashMap<>();
        int usable = 0;
        for (int i = 0; i < transcript.size(); i++) {
            int[] occ = pos.get(transcript.get(i));
            if (occ == null) continue;
            usable++;
            if (occ.length > 400) continue; // "the", "and": no information
            float weight = (float) (1.0 / Math.sqrt(occ.length));
            for (int p : occ) {
                int start = p - i;
                if (near >= 0 && Math.abs(start - near) > radius) continue;
                int key = Math.floorDiv(start, bucket);
                float[] v = votes.get(key);
                if (v == null) votes.put(key, v = new float[2]);
                v[0] += weight;
                v[1] += 1;
            }
        }
        if (usable < transcript.size() * 0.4) return null;
        int bestKey = 0;
        float best = 0, second = 0;
        for (Map.Entry<Integer, float[]> e : votes.entrySet()) {
            float s = e.getValue()[0];
            float[] nb1 = votes.get(e.getKey() - 1), nb2 = votes.get(e.getKey() + 1);
            if (nb1 != null) s += nb1[0] * 0.5f;
            if (nb2 != null) s += nb2[0] * 0.5f;
            if (s > best) {
                second = best;
                best = s;
                bestKey = e.getKey();
            } else if (s > second && Math.abs(e.getKey() - bestKey) > 3) {
                second = s;
            }
        }
        if (best <= 0) return null;
        // Refine: exact start with most in-order hits inside the winning bucket ±bucket.
        int from = Math.max(0, bestKey * bucket - bucket), to = Math.min(n - 1, bestKey * bucket + 2 * bucket);
        int bestStart = from, bestHits = -1;
        for (int s = from; s <= to; s++) {
            int hits = 0;
            for (int i = 0; i < transcript.size(); i++) {
                int w = s + i;
                if (w >= 0 && w < n && (transcript.get(i).equals(text.words[w])
                        || (w + 1 < n && transcript.get(i).equals(text.words[w + 1]))
                        || (w - 1 >= 0 && transcript.get(i).equals(text.words[w - 1])))) hits++;
            }
            if (hits > bestHits) {
                bestHits = hits;
                bestStart = s;
            }
        }
        float hitRatio = bestHits / (float) transcript.size();
        float distinct = second > 0 ? Math.min(1f, (best - second) / best) : 1f;
        float confidence = hitRatio * (0.5f + 0.5f * distinct);
        if (hitRatio < 0.45f || confidence < 0.35f) return null;
        return new Match(bestStart, confidence);
    }

    // ---- persistence --------------------------------------------------------------------------

    public synchronized JSONArray toJson() {
        JSONArray a = new JSONArray();
        for (Anchor x : anchors) {
            if (x.source == SRC_OVERLAY) continue; // rebuilt from the EPUB
            try {
                a.put(new JSONArray().put(x.seconds).put(x.word).put(x.source).put((double) x.confidence));
            } catch (Exception ignored) {
            }
        }
        return a;
    }

    public synchronized void fromJson(JSONArray a) {
        if (a == null) return;
        for (int i = 0; i < a.length(); i++) {
            JSONArray x = a.optJSONArray(i);
            if (x == null) continue;
            anchors.add(new Anchor(x.optDouble(0), x.optInt(1), x.optInt(2), (float) x.optDouble(3, 0.5)));
        }
        Collections.sort(anchors);
    }
}
