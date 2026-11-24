#!/system/bin/sh

GAME_LIST="/sdcard/.lvndThread/lvndgamelist.txt"
RT_PROFILE="SCHED_SP_RT_APP"
sleep_fast=2
sleep_slow=8

mkdir -p /sdcard/.lvndThread
touch "$GAME_LIST"

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

# Toast notification (MENIRU GAYA AXMANAGER)
show_game_toast() {
  case "$1" in
    com.mobile.legends) message="MLBB Detected - lvndThread Active" ;;
    com.tencent.ig) message="PUBG Mobile Detected - lvndThread Active" ;;
    com.dts.freefiremax) message="Free Fire MAX Detected - lvndThread Active" ;;
    *) message="$1 Detected" ;;
  esac

  # Menggunakan am start (bukan am broadcast) agar langsung memanggil Activity
  am start -a "com.lvnderr.os.SHOW_TOAST" -e text "$message" >/dev/null 2>&1
}

echo "lvndThread: Booster Active. Waiting for game..."

last_pkg=""
last_mtime=0
game_list=""

while :; do
  cur_mtime=$(stat -c %Y "$GAME_LIST" 2>/dev/null)

  if [ "$cur_mtime" != "$last_mtime" ]; then
    last_mtime="$cur_mtime"
    game_list="$(grep -v '^#' "$GAME_LIST" 2>/dev/null)"
    last_pkg=""
  fi

  pkg="$(cmd activity stack list 2>/dev/null \
    | awk -F'[:/ ]' '/0,0.*visible=true/{print $5; exit}')"

  [ -z "$pkg" ] && {
    last_pkg=""
    sleep "$sleep_fast"
    continue
  }

  [ "$pkg" = "$last_pkg" ] && {
    sleep "$sleep_fast"
    continue
  }

  last_pkg="$pkg"

  echo "$game_list" | grep -qxF "$pkg" || {
    sleep "$sleep_fast"
    continue
  }

  echo "lvndThread: Boosting $pkg"
  
  # Panggil fungsi Toast
  show_game_toast "$pkg"

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
