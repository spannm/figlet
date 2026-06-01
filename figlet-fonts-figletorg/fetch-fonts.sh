#!/usr/bin/env bash
# fetch-fonts.sh
#
# Downloads FIGfonts (.flf) from figlet.org and filters out fonts with
# restrictive license language.
#
# Usage: run from the figlet-fonts-figletorg module directory.
#   bash fetch-fonts.sh
#
# Requirements: bash, curl, grep, awk, sort, sed

set -uo pipefail

FIGLET_BASE_URL="https://www.figlet.org/fonts"
DEST="src/main/resources/fonts"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

# Restrictive license keywords — a font comment matching any of these is excluded.
RESTRICT_PATTERN='not free|not freely|all rights reserved|permission required|may not be (modified|distributed|sold|used commercially)|for personal use only|personal use only|non-commercial|noncommercial|shareware|do not distribute|must not be (sold|distributed)|not (for )?commercial|no commercial|without (prior )?permission|not public domain|copyright.*all rights'

mkdir -p "$DEST"

# ---------------------------------------------------------------------------
# Step 1: Fetch the directory listing and extract .flf filenames
# ---------------------------------------------------------------------------
echo "Fetching font directory listing from $FIGLET_BASE_URL ..."
curl -s "$FIGLET_BASE_URL/" \
    | grep -oE 'href="[^"]+\.flf"' \
    | sed 's/href="//;s/"//' \
    | sort -u > "$TMP_DIR/all-fonts.txt"

total=$(wc -l < "$TMP_DIR/all-fonts.txt")
echo "Found $total fonts."
echo ""

# ---------------------------------------------------------------------------
# Step 2: Download each font, inspect the license comment, copy if permitted
# ---------------------------------------------------------------------------
count_ok=0
count_skip=0
count_err=0

while IFS= read -r filename; do
    tmp_file="$TMP_DIR/$filename"

    # Download
    if ! curl -s --fail "$FIGLET_BASE_URL/$filename" -o "$tmp_file"; then
        echo "ERROR (download): $filename"
        count_err=$((count_err + 1))
        continue
    fi

    # Validate magic header
    first_line=$(head -1 "$tmp_file")
    if [[ "$first_line" != flf2a* ]]; then
        echo "SKIP (not a valid FIGfont): $filename"
        count_skip=$((count_skip + 1))
        continue
    fi

    # extract comment block length from header field 6
    comment_lines=$(echo "$first_line" | awk '{print $6}')

    # guard: if comment_lines is 0 or non-numeric, allow
    if ! [[ "$comment_lines" =~ ^[0-9]+$ ]] || [ "$comment_lines" -eq 0 ]; then
        cp "$tmp_file" "$DEST/${filename,,}"
        echo "OK (no comment): $filename"
        count_ok=$((count_ok + 1))
        continue
    fi

    # Read exactly comment_lines lines starting at line 2
    comment=$(tail -n +2 "$tmp_file" | head -n "$comment_lines")

    # Filter: skip fonts whose comment contains restrictive language
    if echo "$comment" | grep -qiE "$RESTRICT_PATTERN"; then
        matched=$(echo "$comment" | grep -iE "$RESTRICT_PATTERN" | head -1 | sed 's/^[[:space:]]*//')
        echo "SKIP (license — \"$matched\"): $filename"
        count_skip=$((count_skip + 1))
        continue
    fi

    cp "$tmp_file" "$DEST/${filename,,}"
    echo "OK: $filename"
    count_ok=$((count_ok + 1))

done < "$TMP_DIR/all-fonts.txt"

# ---------------------------------------------------------------------------
# Step 3: Summary (Manifest regeneration skipped due to dynamic registry scanning)
# ---------------------------------------------------------------------------
echo ""
echo "Done."
echo "  Downloaded & Verified : $count_ok"
echo "  Skipped (License)     : $count_skip"
echo "  Errors                : $count_err"
echo "  Target directory      : $DEST"

