#!/usr/bin/env bash
# Builds apps/keyboard/src/main/assets/en.dict: one "word<TAB>frequency" per line.
#
# Which words are valid comes from SCOWL (http://wordlist.aspell.net, permissive licence,
# notice in the asset folder): English, American, British and Australian lists up to size 70,
# with contractions and capitalised words, without possessives. How common each word is comes
# from FrequencyWords' English subtitle counts (https://github.com/hermitdave/FrequencyWords,
# CC-BY-SA-4.0). That list has no apostrophes, so "dont" is counted for "don't".
#
# Usage: scripts/keyboard-dictionary.sh <scowl-2020.12.07 dir> <en_50k.txt>
set -euo pipefail
scowl=$1/final
freq=$2
out=$(dirname "$0")/../apps/keyboard/src/main/assets/en.dict

files=()
for region in english american british australian; do
    for kind in words contractions upper proper-names; do
        for size in 10 20 35 40 50 55 60 70; do
            f=$scowl/$region-$kind.$size
            [[ -f $f ]] && files+=("$f")
        done
    done
done

# Possessives ("dog's") are dropped; contractions ("it's") come from their own lists and stay.
# Single lowercase letters other than "a" are dropped so "i" becomes "I".
{
    printf '%s\n' "${files[@]}" | grep -v contractions | xargs cat | grep -v "'s$"
    printf '%s\n' "${files[@]}" | grep contractions | xargs cat
    # SCOWL leaves out "'s" contractions because they look like possessives.
    printf "%s\n" it\'s that\'s he\'s she\'s what\'s where\'s who\'s there\'s here\'s let\'s \
        how\'s when\'s why\'s everyone\'s everybody\'s someone\'s somebody\'s something\'s \
        nothing\'s nobody\'s everything\'s
} | iconv -f latin1 -t utf-8 | grep -vx "[b-z]" | sort -u |
    awk -v freqfile="$freq" '
    BEGIN {
        while ((getline line < freqfile) > 0) {
            split(line, p, " ")
            count[p[1]] = p[2]
        }
    }
    { words[NR] = $0; key = tolower($0); gsub("\047", "", key); keys[NR] = key; forms[key]++ }
    END {
        for (i = 1; i <= NR; i++) {
            w = words[i]; key = keys[i]
            c = (key in count) ? count[key] + 0 : 0
            # Several words share a count ("its" and "it'\''s", "will" and "Will"): split it.
            if (forms[key] > 1) {
                if (w != tolower(w)) c = int(c / 20)
                else c = int(c / 2)
            }
            if (c < 1) c = 1
            print w "\t" c
        }
    }' | sort -t$'\t' -k2,2nr -k1,1 > "$out"
wc -l "$out"
