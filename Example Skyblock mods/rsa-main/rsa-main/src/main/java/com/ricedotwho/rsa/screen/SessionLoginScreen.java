package com.ricedotwho.rsa.screen;

import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.MalformedJsonException;
import com.ricedotwho.rsa.utils.api.SessionAPI;
import com.ricedotwho.rsm.utils.Accessor;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.text.Text;
import net.minecraft.client.session.Session;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextWidget;

public class SessionLoginScreen extends Screen implements Accessor {
   private static final Pattern TOKEN_REGEX = Pattern.compile("(?:accessToken:\"|token:)?([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)");
   private static SessionLoginScreen instance;
   private static Session user;
   private final Screen parent;
   private TextFieldWidget sessionField;
   private String feedBackMessage = "";
   private int feedBackColor = -1;
   private int centerX = 0;
   private int centerY = 0;

   public static SessionLoginScreen getInstance() {
      if (instance == null) {
         instance = new SessionLoginScreen(null);
      }

      return instance;
   }

   private SessionLoginScreen(Screen parent) {
      super(Text.literal("SessionLogin"));
      this.parent = parent;
   }

   public void close() {
      mc.setScreen(this.parent);
   }

   protected void init() {
      TextWidget ssidText = new TextWidget(Text.literal("SSID"), mc.textRenderer);
      this.centerX = this.width / 2 - 50;
      this.centerY = 60;
      this.sessionField = new TextFieldWidget(mc.textRenderer, 100, 20, user == null ? Text.empty() : Text.literal(user.getSessionId()));
      ssidText.setWidth(100);
      ssidText.setPosition(this.centerX, this.centerY + 35);
      this.sessionField.setPosition(this.centerX, this.centerY + 45);
      this.sessionField.setMaxLength(10000);
      this.addDrawableChild(ssidText);
      this.addDrawableChild(this.sessionField);
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Login"), button -> this.login())
            .width(100)
            .position(this.centerX, this.centerY + 70)
            .build()
      );
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Reset"), button -> reset())
            .width(100)
            .position(this.centerX, this.centerY + 95)
            .build()
      );
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Copy SSID"), button -> copySSID())
            .width(100)
            .position(this.centerX, this.centerY + 120)
            .build()
      );
      this.addDrawableChild(
         ButtonWidget.builder(Text.literal("Back"), button -> this.close())
            .width(100)
            .position(this.centerX, this.centerY + 145)
            .build()
      );
   }

   public void render(DrawContext gfx, int mouseX, int mouseY, float deltaTicks) {
      gfx.drawText(
         mc.textRenderer,
         this.feedBackMessage,
         this.centerX + 50 - (mc.textRenderer.getWidth(this.feedBackMessage) >> 1),
         this.centerY,
         this.feedBackColor,
         true
      );
      String currentUser = "Current Account: " + mc.getSession().getUsername();
      gfx.drawText(mc.textRenderer, currentUser, this.centerX + 50 - (mc.textRenderer.getWidth(currentUser) >> 1), this.centerY + 10, -1, true);
      super.render(gfx, mouseX, mouseY, deltaTicks);
   }

   private void login() {
      if (this.sessionField.getText().isBlank()) {
         this.feedBackMessage = "Please enter an SSID!";
         this.feedBackColor = -7405568;
      } else {
         String ssidText = this.parseToken(this.sessionField.getText().trim());
         String[] info = null;
         int i = 0;

         while (i < 10) {
            try {
               info = SessionAPI.getProfileInfo(ssidText);
               break;
            } catch (JsonSyntaxException | MalformedJsonException e) {
               this.feedBackMessage = "Ran out of retries, network error!";
               this.feedBackColor = -7405568;
               System.err.println("Failed to parse json! Retries left: " + i);
               i++;
            } catch (IOException e) {
               this.feedBackMessage = "Failed to poll API for username and UUID!";
               this.feedBackColor = -7405568;
               return;
            } catch (Exception e) {
               this.feedBackMessage = "Invalid SSID!";
               e.printStackTrace();
               this.feedBackColor = -7405568;
               return;
            }
         }

         if (info != null) {
            try {
               user = new Session(info[0], SessionAPI.undashedToUUID(info[1]), ssidText, Optional.empty(), Optional.empty());
            } catch (Exception e) {
               this.feedBackMessage = "Failed to parse UUID from string!";
               this.feedBackColor = -7405568;
               return;
            }

            this.feedBackMessage = "Successfully updated session!";
            this.feedBackColor = -16739323;
         }
      }
   }

   private String parseToken(String input) {
      if (input != null && !input.isEmpty()) {
         Matcher matcher = TOKEN_REGEX.matcher(input);
         return matcher.find() ? matcher.group(1) : "";
      } else {
         return "";
      }
   }

   public static void reset() {
      user = null;
   }

   public static void copySSID() {
      mc.keyboard.setClipboard(mc.getSession().getAccessToken());
   }

   public static Session getUser() {
      return user;
   }
}
