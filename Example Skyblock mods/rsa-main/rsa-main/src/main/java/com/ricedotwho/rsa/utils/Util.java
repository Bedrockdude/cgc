package com.ricedotwho.rsa.utils;

import com.ricedotwho.rsm.utils.Accessor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.tick.TickManager;

public final class Util implements Accessor {
   private static final Pattern timestampPattern = Pattern.compile("(\\d+)\\s*([dhms])");

   public static void setTickRate(float tickRate, boolean frozen) {
      if (!(tickRate > 20.0F) && !(tickRate < 0.0F)) {
         TickManager tickRateManager = mc.world.getTickManager();
         tickRateManager.setTickRate(tickRate);
         tickRateManager.setFrozen(frozen);
         if (frozen) {
            tickRateManager.setStepTicks(0);
         }
      } else {
         throw new IllegalArgumentException("tickRate must be between 0 and 20!");
      }
   }

   public static void setTickRate(float tickRate) {
      setTickRate(tickRate, tickRate == 0.0F);
   }

   public static boolean isZero() {
      if (FabricLoader.getInstance().isModLoaded("zeroclient")) {
         try {
            Class<?> clazz = Class.forName("com.ricedotwho.zero.ZeroClient");
            return (Boolean)clazz.getMethod("isZero").invoke(null);
         } catch (Throwable e) {
            return false;
         }
      }

      return false;
   }

   public static long getMillisFromDHMS(String input) {
      Matcher matcher = timestampPattern.matcher(input);
      long total = 0L;

      while (matcher.find()) {
         long value = Long.parseLong(matcher.group(1));
         char unit = matcher.group(2).charAt(0);
         switch (unit) {
            case 'd':
               total += value * 86400000L;
               break;
            case 'h':
               total += value * 3600000L;
               break;
            case 'm':
               total += value * 60000L;
               break;
            case 's':
               total += value * 1000L;
         }
      }

      return total;
   }

   public static String millisToDHMS(long millis) {
      long days = millis / 86400000L;
      long hours = millis / 3600000L % 24L;
      long minutes = millis / 60000L % 60L;
      long seconds = millis / 1000L % 60L;
      StringBuilder sb = new StringBuilder();
      if (days > 0L) {
         sb.append(days).append("d ");
      }

      if (hours > 0L) {
         sb.append(hours).append("h ");
      }

      if (minutes > 0L) {
         sb.append(minutes).append("m ");
      }

      if (seconds > 0L) {
         sb.append(seconds).append("s");
      }

      return sb.toString().trim();
   }

   private Util() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
