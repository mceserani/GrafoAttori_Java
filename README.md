# Progetto IMDB — Parte Java (CreaGrafo)

Questo documento descrive in modo **completo e operativo** le specifiche del **solo programma Java** per la costruzione del grafo degli attori a partire dai file TSV di IMDB. Il programma produce i file di output necessari alle fasi successive del progetto (C/Python), ma qui ci limitiamo all’ambito Java.

---

## 1) Obiettivo

Leggere i dataset IMDB `name.basics.tsv` e `title.principals.tsv`, selezionare gli **attori/attrici** con **anno di nascita noto**, assegnare un **codice intero** a ciascun attore e costruire il **grafo di co-partecipazione**: due attori sono collegati da un arco se hanno recitato nello **stesso titolo** almeno una volta.

Il programma deve poi **scrivere su file**:

* `out/nomi.tsv` — anagrafica degli attori selezionati (ordinata per codice crescente)
* `out/grafo.tsv` — lista di adiacenza del grafo (una riga per attore; vicini **ordinati** e **senza duplicati**)
* **Opzionale**: `out/partecipazioni.txt` — elenco, per attore, dei codici dei titoli a cui ha partecipato (usato da uno script Python esterno).

---

## 2) Dipendenze e requisiti

* **Java 11+**
* Libreria **fastutil** (consigliata) per collezioni primitive:

  * `it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap`
  * `it.unimi.dsi.fastutil.ints.IntOpenHashSet` / `IntSet`
* I **file TSV** non devono essere versionati nel repo (troppo grandi); si consiglia di metterli in `data/` o usare symlink.

> **Nota**: se non è possibile usare fastutil, si può ripiegare su `HashMap<Integer,Attore>` + strutture custom `int[]`/liste/insiemi leggeri; le specifiche funzionali non cambiano.

---

## 3) Formato degli input

### 3.1 `name.basics.tsv`

* Campi rilevanti (tab-separati):

  1. `nconst` (es. `nm0000148`)
  2. `primaryName`
  3. `birthYear` (oppure `\N` se sconosciuto)
  4. `primaryProfession` (contiene stringhe comma-separate: es. `actor,producer`)

**Filtro attori:**

* includere la riga **solo se** `birthYear` è noto (**≠ `\N`**) **e** `primaryProfession` contiene **`actor`** oppure **`actress`**.

**Codice attore:**

* da `nconst` si estrae la **parte numerica** e la si converte in `int`. Questo intero è la **chiave** nelle strutture e negli output.

### 3.2 `title.principals.tsv`

* Campi rilevanti:

  1. `tconst` (es. `tt1234567`)
  2. `nconst` (persona coinvolta nel titolo)

**Uso:**

* Scansione **una sola volta** in ordine; per ogni `tconst` si raccoglie il **cast** degli **attori selezionati** (cioè presenti nella mappa costruita dal file `name.basics.tsv`).
* Per ogni titolo si collegano **tutte le coppie non ordinate (u,v)** del cast (simmetria degli archi) evitando self-loop.

**Codice titolo:**

* da `tconst` si estrae la parte numerica (`int`) solo se si genera `partecipazioni.txt`.

---

## 4) Output richiesti

### 4.1 `out/nomi.tsv`

* Una riga per attore, **ordinata per codice crescente**:

```
<codice>\t<nome>\t<anno>
```

### 4.2 `out/grafo.tsv`

* Una riga per attore (stesso **ordine** di `nomi.tsv`), con **grado** e **lista dei vicini ordinati**:

```
<codice>\t<numcop>\t<v1>\t<v2>\t...\t<vk>
```

* La lista dei vicini:

  * **senza duplicati**
  * **ordinata per codice crescente**
  * **simmetrica**: se `u` include `v`, anche `v` include `u`.

### 4.3 `out/partecipazioni.txt` (opzionale, “progetto completo”)

* Una riga per **(attore, titolo)**, oppure una riga per attore con lista di titoli. Le specifiche adottate in questo progetto: una riga per **attore** con lista dei **codici titolo** (interi) **ordinati** e deduplicati:

```
<attoreCodice>\t<t1>\t<t2>\t...\t<tk>
```

* Il file può risultare molto grande (centinaia di MB).

---

## 5) Algoritmo (alto livello)

1. **Parsing attori** (`name.basics.tsv`):

   * per ogni riga, applica i filtri (anno noto, professione contiene actor/actress);
   * estrai il **codice attore** (intero), costruisci l’oggetto `Attore` e inseriscilo in `Int2ObjectOpenHashMap<Attore>`.
2. **Costruzione grafo** (`title.principals.tsv`):

   * scansiona una sola volta; mantieni un buffer **cast** per il `tconst` corrente (riusalo a ogni cambio titolo);
   * al cambio di titolo: **deduplica** il cast e per tutte le coppie `(u,v)` aggiorna i set di coprotagonisti (`u.co.add(v)`, `v.co.add(u)`). Se richiesto, accumula anche le **partecipazioni** (attore → titolo).
3. **Finalizzazione**:

   * converti il set `co` di ogni attore in `int[] adj` **ordinato**;
   * scrivi `out/nomi.tsv` e `out/grafo.tsv` rispettando i formati e l’**ordinamento per codice**.

---

## 6) Strutture dati

* **Mappa principale**: `Int2ObjectOpenHashMap<Attore>` — chiave = codice attore (`int`), valore = record `Attore`.
* **Coprotagonisti (fase build)**: `IntOpenHashSet` per ogni attore — evita duplicati durante la costruzione.
* **Adiacenze finali**: `int[] adj` per attore — lista **immutabile** e **ordinata** dei vicini.
* **Buffer cast per titolo**: `IntOpenHashSet` oppure `IntArrayList` (con `sort + dedup`) riutilizzato per ciascun `tconst`.

> **Motivazioni**: evitare autoboxing (`int` ↔ `Integer`), ridurre memoria, massimizzare località di cache; ordinare e “congelare” le adiacenze a fine build per avere output deterministico e leggero.

---

## 7) Regole di parsing e casi particolari

* I campi sconosciuti sono `\N`: vanno **ignorate** le righe degli attori con `birthYear=\N`.
* Il test su `primaryProfession` è un semplice `contains("actor") || contains("actress")` (case-sensitive come nei TSV originali).
* `nconst`/`tconst`: estrarre solo le cifre finali e convertirle in `int` (es. `nm0012345` → `12345`).
* Evitare **self-loop** (`u != v`) quando si generano le coppie nel cast.
* Garantire la **simmetria degli archi**: inserire sempre in entrambe le direzioni.

---

## 8) Vincoli prestazionali (indicativi)

* Una **sola scansione** per file di input (no multi-pass su dataset grandi).
* Tempo complessivo entro pochi minuti su macchina didattica tipica; uso memoria compatibile con i dataset assegnati.
* Preallocare capacità della mappa (es. ~512k, load factor 0.75) per ridurre i resize.
* Riutilizzare i buffer (es. `cast.clear()` tra i titoli) per limitare la pressione sul GC.

---

## 9) Ordine e qualità degli output

* `nomi.tsv` e `grafo.tsv` devono essere **coerenti** (stesso ordinamento per codice).
* Gli **adiacenti** devono essere **ordinati** e **senza duplicati**.
* L’output deve essere **deterministico** a parità di input.

---

## 10) Interfaccia a riga di comando

```bash
# Obbligatorio:
java CreaGrafo <path/name.basics.tsv> <path/title.principals.tsv>

# Opzionale: genera anche le partecipazioni
java CreaGrafo <path/name.basics.tsv> <path/title.principals.tsv> -p
```

**Output generati** (nella cartella `out/`): `nomi.tsv`, `grafo.tsv`, e se richiesto `partecipazioni.txt`.

---

## 11) Build & run (con fastutil)

```bash
# Compilazione (assumendo fastutil.jar accanto ai sorgenti)
cd src-java
javac -cp fastutil.jar CreaGrafo.java

# Esecuzione
java -cp .:fastutil.jar CreaGrafo ../data/name.basics.tsv ../data/title.principals.tsv -p
```

> Se non si usa fastutil: sostituire i tipi fastutil con `HashMap<Integer,Attore>` e strutture `int[]`/liste custom; le regole di filtraggio, ordinamento e scrittura restano identiche.

---

## 12) Strategia di test (Java)

1. **Mini-dataset**: crea versioni ridotte dei TSV (es. `head -n 100000`), verifica che il programma completi e produca i file.
2. **Coerenza**: per un sottoinsieme, controlla manualmente che se `A` ha `B` in `grafo.tsv`, anche `B` abbia `A`.
3. **Ordinamento**: verifica che `nomi.tsv` e `grafo.tsv` siano ordinati per codice, e che i vicini siano in ordine crescente.
4. **Prestazioni**: controlla tempo e memoria con dataset di dimensione intermedia.

---

## 13) Error handling & robustezza

* Segnalare su `stderr` file mancanti o illeggibili ed **uscire con codice ≠ 0**.
* Saltare righe **malformate** senza interrompere l’elaborazione (loggare un contatore se utile).
* Usare `try-with-resources` per chiudere i reader/writer.

---

## 14) Estensioni (facoltative ma utili)

* Scrivere un **conteggio riassuntivo** a fine esecuzione: #righe lette, #attori selezionati, tempo totale.
* Esportare anche **statistiche**: grado medio, massimi, distribuzione (su dataset piccoli per debug).
* Aggiungere un flag CLI per indicare la cartella `out/` personalizzata.

---

## 15) Riepilogo dei contratti (da rispettare strettamente)

* Filtri: `birthYear` noto **e** professione contiene `actor|actress`.
* Codifica chiavi: convertire `nm...`/`tt...` in interi (parte numerica).
* Simmetria archi; nessun self-loop.
* Output **ordinati** e **senza duplicati**.
* Una **sola scansione** per file.
* Strutture **primitive** dove possibile (fastutil) e conversione a `int[]` per lo stato f
