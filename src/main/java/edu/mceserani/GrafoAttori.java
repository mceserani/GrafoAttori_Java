package edu.mceserani;

import java.io.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

/**
 * Punto A1: lettura di name.basics.tsv e costruzione della mappa
 * Int2ObjectOpenHashMap<Integer, Attore> (chiave = codice attore int).
 *
 * Uso (solo A1):
 *   javac -cp fastutil.jar CreaGrafo.java
 *   java  -cp .:fastutil.jar CreaGrafo ../data/name.basics.tsv
 */
public class GrafoAttori {

	private GrafoAttori() { } // non instanziabile

    // ===================== Modello dati (A1) =====================
    public static final class Attore {
        public final int codice;   // codice attore come int (da nm...)
        public final String nome;  // primaryName
        public final int anno;     // birthYear
        // In A2 useremo questo set per accumulare i coprotagonisti (evita duplicati).
        public final IntSet co;    // insieme di codici attori adiacenti (temporaneo)
        Attore(int codice, String nome, int anno) {
            this.codice = codice;
            this.nome = nome;
            this.anno = anno;
            this.co = new IntOpenHashSet();
        }
    }

    // ===================== MAIN (A1) =====================
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Uso: java CreaGrafo <path/name.basics.tsv>");
            System.exit(1);
        }
        String nameBasicsPath = args[0];

        // Preallocazione: ~383k attori previsti → capacity ~512k con LF 0.75
        Int2ObjectOpenHashMap<Attore> attoriByCodice = new Int2ObjectOpenHashMap<>(512_000, 0.75f);

        int nTot = 0, nAttori = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(nameBasicsPath))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                nTot++;
                String[] f = line.split("\t", -1);
                if (f.length < 5) continue; // riga malformata
                String nconst = f[0];          // es. nm0000148
                String primaryName = f[1];
                String birthYear = f[2];       // es. 1963 oppure \N
                String professions = f[4];     // es. actor,producer

                if (isUnknown(birthYear)) continue; // serve anno noto
                if (!isActorProfession(professions)) continue; // deve contenere actor/actress

                int codice = parseNumericId(nconst); // nm... → int
                int anno = safeParseInt(birthYear);

                Attore a = new Attore(codice, primaryName, anno);
                attoriByCodice.put(codice, a);
                nAttori++;
            }
        }

        // Log di verifica rapido (solo A1)
        System.out.println("[A1] Righe lette: " + nTot);
        System.out.println("[A1] Attori selezionati: " + nAttori);
        System.out.println("[A1] Dimensione mappa: " + attoriByCodice.size());
    }

    // ===================== Utility parsing =====================
    private static boolean isUnknown(String s) {
        return s == null || s.isEmpty() || "\\N".equals(s);
    }

    private static boolean isActorProfession(String profs) {
        if (profs == null) return false;
        // match semplice (case-sensitive come nei TSV IMDB)
        return profs.contains("actor") || profs.contains("actress");
    }

    /** Estrae la parte numerica da un id IMDB (nmXXXX / ttXXXX). */
    private static int parseNumericId(String imdbId) {
        int n = 0; int len = imdbId.length();
        for (int i = 0; i < len; i++) {
            char ch = imdbId.charAt(i);
            if (ch >= '0' && ch <= '9') {
                n = n * 10 + (ch - '0');
            }
        }
        return n;
    }

    private static int safeParseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }
}