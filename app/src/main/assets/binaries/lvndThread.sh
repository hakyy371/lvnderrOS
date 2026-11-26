#!/system/bin/sh

GAME_LIST="/sdcard/.lvndThread/lvndgamelist.txt"
RT_PROFILE="SCHED_SP_RT_APP"

sleep_fast=2
sleep_slow=5

mkdir -p /sdcard/.lvndThread
touch "$GAME_LIST"

# === DAFTAR DEFAULT SEMUA GAME (Sesuai UI) ===
if [ ! -s "$GAME_LIST" ]; then
  echo "com.mobile.legends" > "$GAME_LIST"
  echo "com.dts.freefiremax" >> "$GAME_LIST"
  echo "com.tencent.ig" >> "$GAME_LIST"
  echo "com.activision.callofduty.shooter" >> "$GAME_LIST"
  echo "com.roblox.client" >> "$GAME_LIST"
  echo "com.miHoYo.GenshinImpact" >> "$GAME_LIST"
  echo "com.riotgames.league.wildrift" >> "$GAME_LIST"
  echo "com.mojang.minecraftpe" >> "$GAME_LIST"
  echo "com.HoYoverse.hkrpgoversea" >> "$GAME_LIST"
  echo "com.garena.game.kgid" >> "$GAME_LIST"
  echo "com.supercell.clashofclans" >> "$GAME_LIST"
  echo "com.supercell.clashroyale" >> "$GAME_LIST"
  echo "com.gameloft.android.ANMP.GloftA9HM" >> "$GAME_LIST"
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

show_game_toast() {
  case "$1" in
    com.mobile.legends) message="MLBB Detected - lvndThread Active" ;;
    com.tencent.ig) message="PUBG Mobile Detected - lvndThread Active" ;;
    com.dts.freefiremax) message="Free Fire MAX Detected - lvndThread Active" ;;
    com.activision.callofduty.shooter) message="COD Mobile Detected - lvndThread Active" ;;
    com.miHoYo.GenshinImpact) message="Genshin Impact Detected - lvndThread Active" ;;
    com.roblox.client) message="Roblox Detected - lvndThread Active" ;;
    *) message="$1 Detected - lvndThread Active" ;;
  esac

  am start -a "com.lvnderr.os.SHOW_TOAST" -e text "$message" >/dev/null 2>&1
}

echo "lvndThread: Booster Active. Waiting for game..."

current_boosted_pkg=""

while :; do
  # Baca ulang list game (jika ada update dari aplikasi)
  game_list="$(grep -v '^#' "$GAME_LIST" 2>/dev/null)"

  # Deteksi aplikasi yang sedang terbuka
  pkg="$(dumpsys window | grep mCurrentFocus | awk '{print $3}' | cut -d'/' -f1)"
  
  if [ -z "$pkg" ]; then
      pkg="$(cmd activity stack list 2>/dev/null | awk -F'[:/ ]' '/0,0.*visible=true/{print $5; exit}')"
  fi

  if [ -z "$pkg" ]; then
    current_boosted_pkg=""
    sleep "$sleep_fast"
    continue
  fi

  # Cek apakah aplikasi yang dibuka ada di dalam Game List
  if echo "$game_list" | grep -qxF "$pkg"; then
      
      if [ "$pkg" != "$current_boosted_pkg" ]; then
          success=0
          
          for pid in $(pidof "$pkg" 2>/dev/null); do
              for _ in 1 2 3 4 5 6; do
                  tid="$(find_render_tid "$pid")"
                  if [ -n "$tid" ]; then
                      settaskprofile "$tid" "$RT_PROFILE" 2>/dev/null
                      success=1
                      break 2
                  fi
                  sleep 1
              done
          done

          if [ "$success" -eq 1 ]; then
              echo "lvndThread: Successfully boosted $pkg"
              show_game_toast "$pkg"
              current_boosted_pkg="$pkg"
          else
              echo "lvndThread: Game $pkg detected but RenderThread not ready yet..."
          fi
      fi
  else
      current_boosted_pkg=""
  fi

  sleep "$sleep_slow"
done
