#!/bin/bash
# Launches each PrismLauncher instance in turn and checks for a new crash report.

set -u

INSTANCES_DIR="/var/home/sm0keskreen/.var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances"
STARTUP_GRACE_SECONDS=3

# name -> loader, for the summary printed at the end
declare -A LOADER=(
  ["1.17-vanilla"]="Vanilla"
  ["1.17"]="Fabric"
  ["1.18-vanilla"]="Vanilla"
  ["1.18"]="Fabric"
  ["1.18(1)"]="Forge"
  ["1.19-vanilla"]="Vanilla"
  ["1.19"]="Fabric"
  ["1.19(1)"]="Forge"
  ["1.20-vanilla"]="Vanilla"
  ["1.20"]="Fabric"
  ["1.20(1)"]="Forge"
  ["1.21.11"]="Vanilla"
  ["1.21.11(1)"]="Fabric"
  ["1.21.11(2)"]="Forge"
  ["26.2"]="Fabric"
  ["26.1.2"]="Vanilla"
  ["26.1.2(2)"]="Forge"
)

ORDER=("1.17-vanilla" "1.17" "1.18-vanilla" "1.18" "1.18(1)" \
       "1.19-vanilla" "1.19" "1.19(1)" "1.20-vanilla" "1.20" "1.20(1)" \
       "1.21.11" "1.21.11(1)" "1.21.11(2)" "26.2" "26.1.2" "26.1.2(2)")

PASSED=()
STOPPED_AT=""

is_anything_prism_open() {
  # Checks the whole sandbox, not the specific instance's process.
  pgrep -f "bwrap.*prismlauncher|PrismLauncher" >/dev/null 2>&1
}

for name in "${ORDER[@]}"; do
  loader="${LOADER[$name]}"
  game_dir="$INSTANCES_DIR/$name/minecraft"
  crash_dir="$game_dir/crash-reports"

  echo ""
  echo "=================================================="
  echo "  Launching: $name ($loader)"
  echo "=================================================="

  if [ ! -d "$game_dir" ]; then
    echo "  SKIP: instance directory not found at $game_dir"
    continue
  fi

  before=""
  if [ -d "$crash_dir" ]; then
    before=$(ls -1 "$crash_dir" 2>/dev/null | sort)
  fi

  flatpak run org.prismlauncher.PrismLauncher --launch "$name" >/dev/null 2>&1

  echo "  Giving it ${STARTUP_GRACE_SECONDS}s to start, then watching until nothing Prism-related is open..."
  sleep "$STARTUP_GRACE_SECONDS"

  while is_anything_prism_open; do
    sleep 1
  done

  echo "  $name is no longer running."

  after=""
  if [ -d "$crash_dir" ]; then
    after=$(ls -1 "$crash_dir" 2>/dev/null | sort)
  fi
  new_crashes=$(comm -13 <(echo "$before") <(echo "$after"))

  if [ -n "$new_crashes" ]; then
    echo ""
    echo "  !!! CRASH DETECTED on $name ($loader) !!!"
    echo "  New crash report(s):"
    while IFS= read -r f; do
      echo "    $crash_dir/$f"
    done <<< "$new_crashes"
    STOPPED_AT="$name ($loader)"
    break
  fi

  echo "  No new crash report, clean exit. Moving on."
  PASSED+=("$name ($loader)")
done

echo ""
echo "=================================================="
echo "  SUMMARY"
echo "=================================================="
echo "  Passed (${#PASSED[@]}):"
for p in "${PASSED[@]}"; do
  echo "    - $p"
done
if [ -n "$STOPPED_AT" ]; then
  echo "  Stopped at: $STOPPED_AT"
else
  echo "  All instances completed with no crashes."
fi
