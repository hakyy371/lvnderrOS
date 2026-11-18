#!/system/bin/sh

GAME_LIST="/sdcard/.lvndThread/lvndgamelist.txt"
RT_PROFILE="SCHED_SP_RT_APP"
sleep_fast=2
sleep_slow=8

# Pastikan folder dan list game sudah ada
mkdir -p /sdcard/.lvndThread
touch "$GAME_LIST"

# Daftar game bawaan (jika list masih kosong)
if [ ! -s "$GAME_LIST" ]; then
  echo "com.mobile.legends" > "$GAME_LIST"
  echo "com.dts.freefiremax" >> "$GAME_LIST"
  echo "com.tencent.ig" >> "$GAME_LIST"
fi

find_render_tid() {
  pid="$1"
  for t in /proc/$pid/task/*; do
    [ -f "$t/comm" ] || continue
    read -r name < "$t/comm"
    [ "$name" = "RenderThread" ] && {
      echo "${t##*/}"
      return 0
    }
  done
  return 1
}

echo "lvndThread: Booster Active. Waiting for game..."
last_pkg=""
last_mtime=0
game_list=""

while :; do
  # Baca ulang list jika ada perubahan (misal: user menambah game baru)
  cur_mtime=$(stat -c %Y "$GAME_LIST" 2>/dev/null)
  if [ "$cur_mtime" != "$last_mtime" ]; then
    last_mtime="$cur_mtime"
    game_list="$(grep -v '^#' "$GAME_LIST" 2>/dev/null)"
    last_pkg=""
  fi

  # Cek aplikasi yang sedang terbuka
  pkg="$(cmd activity stack list 2>/dev/null \
    | awk -F'[:/ ]' '/0,0.*visible=true/{print $5; exit}')"

  [ -z "$pkg" ] && { sleep "$sleep_fast"; continue; }
  [ "$pkg" = "$last_pkg" ] && { sleep "$sleep_fast"; continue; }
  
  last_pkg="$pkg"

  # Cek apakah aplikasi yang dibuka ada di dalam list
  echo "$game_list" | grep -qxF "$pkg" || {
    sleep "$sleep_fast"
    continue
  }

  echo "lvndThread: Boosting $pkg"
  
  # Cari RenderThread dan beri profil prioritas tinggi
  for pid in $(pidof "$pkg" 2>/dev/null); do
    for _ in 1 2 3; do
      tid="$(find_render_tid "$pid")"
      [ -n "$tid" ] && break
      sleep 0.4
    done

    [ -n "$tid" ] && settaskprofile "$tid" "$RT_PROFILE" 2>/dev/null
  done

  sleep "$sleep_slow"
done