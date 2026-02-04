# Petit projet – Automate (clone egrep, ERE partiel)

## Utilisation

```bash
java PetitProjetRegex "<regex>" [fichier]
```

- Sans fichier : lecture sur l’entrée standard (stdin).
- Avec fichier : recherche dans le fichier indiqué.

### Exemple de l’énoncé (Figure 1)

```bash
# Compilation (une fois)
javac EgrepV1.java
javac PetitProjetRegex.java

# Même résultat que egrep "S(a|g|r)+on" 56667-0.txt
java PetitProjetRegex "S(a|g|r)+on" text.txt
```

Pour reproduire exactement la figure avec le livre Gutenberg :

1. Télécharger le fichier : https://www.gutenberg.org/files/56667/56667-0.txt  
2. Le sauvegarder par exemple sous `56667-0.txt` ou `text.txt` dans ce dossier.  
3. Lancer : `java PetitProjetRegex "S(a|g|r)+on" 56667-0.txt`

(Un exemplaire téléchargé peut déjà exister sous le nom `56667-0-full.txt`.)

## Syntaxe RegEx supportée (ERE partiel)

| Symbole | Signification        |
|--------|----------------------|
| `a`    | Lettre ASCII         |
| `.`    | N’importe quel caractère |
| `*`    | Zéro ou plus (Kleene) |
| `+`    | Un ou plus           |
| `\|`   | Alternative (ou)     |
| `()`   | Parenthèses (groupe) |

Concaténation : pas d’opérateur, par ex. `ab` = a puis b.

## Chaîne de traitement

1. RegEx → arbre de syntaxe  
2. Arbre → ε-NFA (Aho-Ullman)  
3. ε-NFA → DFA (construction par sous-ensembles)  
4. DFA → DFA minimal  
5. Recherche : chaque ligne du fichier est testée contre l’automate (recherche de facteur reconnu).

## Fichiers

- `PetitProjetRegex.java` : point d’entrée (lance EgrepV1).
- `EgrepV1.java` : moteur (parse, NDFA, DFA, minimisation, recherche).
- `text.txt` : fichier de test.
- `56667-0-full.txt` : optionnel, livre Gutenberg pour tests à grande échelle.
