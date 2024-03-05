/*
 * Copyright (c) 2017 Vimeo  (https://vimeo.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.castingapp;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;
import com.google.android.gms.cast.framework.media.ImageHints;
import com.google.android.gms.cast.framework.media.ImagePicker;
import com.google.android.gms.cast.framework.media.MediaIntentReceiver;
import com.google.android.gms.cast.framework.media.NotificationOptions;
import com.google.android.gms.common.images.WebImage;
import java.util.Arrays;
import java.util.List;

/**
 * Provider used to customize ChromeCast's route selector and notification.
 */
public class CastOptionsProvider implements OptionsProvider {

  private static final String TAG = "CAST_PROVIDER_INFO";

  @Override
  public CastOptions getCastOptions(Context context) {
    String expandedControllerClassName = getExpandedControlllerClassName(
            context
    );
    NotificationOptions notificationOptions = new NotificationOptions.Builder()
            .setSmallIconDrawableResId(getNotificationIconResId(context))
            .setTargetActivityClassName(expandedControllerClassName)
            .setActions(
                    Arrays.asList(
                            MediaIntentReceiver.ACTION_REWIND,
                            MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                            MediaIntentReceiver.ACTION_STOP_CASTING
                    ),
                    new int[] { 1, 2 }
            )
            .build();

    CastMediaOptions.Builder mediaOptionsBuilder = new CastMediaOptions.Builder()
            .setImagePicker(new ImagePickerImpl())
            .setNotificationOptions(notificationOptions);
    if (expandedControllerClassName != null) {
      mediaOptionsBuilder.setExpandedControllerActivityClassName(
              expandedControllerClassName
      );
    }

    String castAppId = context.getString(R.string.chromecast_receiver_id);
    if (castAppId.isEmpty()) {
      castAppId = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;
    }
    return new CastOptions.Builder()
            .setReceiverApplicationId(castAppId)
            .setCastMediaOptions(mediaOptionsBuilder.build())
            .build();
  }

  @Override
  public List<SessionProvider> getAdditionalSessionProviders(Context context) {
    return null;
  }

  private static class ImagePickerImpl extends ImagePicker {

    @Override
    public WebImage onPickImage(
            MediaMetadata mediaMetadata,
            @NonNull ImageHints imageHints
    ) {
      if ((mediaMetadata == null) || !mediaMetadata.hasImages()) {
        return null;
      }
      List<WebImage> images = mediaMetadata.getImages();
      if (images.size() == 1) {
        return images.get(0);
      } else if (images.size() > 0) {
        if (
                imageHints.getType() ==
                        ImagePicker.IMAGE_TYPE_MEDIA_ROUTE_CONTROLLER_DIALOG_BACKGROUND
        ) {
          return images.get(1);
        } else {
          return images.get(0);
        }
      }
      return null;
    }
  }

  private String getExpandedControlllerClassName(Context context) {
    try {
      ApplicationInfo ai = context
              .getPackageManager()
              .getApplicationInfo(
                      context.getPackageName(),
                      PackageManager.GET_META_DATA
              );
      Bundle bundle = ai.metaData;
      String classNameValue = bundle.getString(
              context.getString(
                      R.string.chromecast_metadata_expanded_controller_activity_tag
              )
      );
      if (classNameValue != null) {
        Log.e(TAG, "Expanded cast controller found: " + classNameValue);
        return classNameValue;
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "Failed to load meta-data, NameNotFound: " + e.getMessage());
    } catch (NullPointerException e) {
      Log.e(TAG, "Failed to load meta-data, NullPointer: " + e.getMessage());
    }
    return null;
  }

  private int getNotificationIconResId(Context context) {
    final String DRAWABLE_TYPE = "drawable";
    final String MIPMAP_TYPE = "mipmap";
    try {
      ApplicationInfo ai = context
              .getPackageManager()
              .getApplicationInfo(
                      context.getPackageName(),
                      PackageManager.GET_META_DATA
              );
      Bundle bundle = ai.metaData;
      String iconResourcePath = bundle.getString(
              context.getString(R.string.chromecast_metadata_notification_icon_tag)
      );
      if (iconResourcePath != null) {
        String[] pathSegments = iconResourcePath.split("/");
        if (pathSegments.length > 0) {
          boolean isMipmap = false;
          // Check in path if resource is a mipmap, if not, assume it's a drawable [AR]
          for (int i = 0; i < pathSegments.length - 1; i++) {
            if (pathSegments[i].contains(MIPMAP_TYPE)) {
              isMipmap = true;
              break;
            }
          }
          String resourceName =
                  pathSegments[pathSegments.length - 1].replaceFirst("[.][^.]+$", "");
          int resId = context
                  .getResources()
                  .getIdentifier(
                          resourceName,
                          isMipmap ? MIPMAP_TYPE : DRAWABLE_TYPE,
                          context.getPackageName()
                  );
          if (resId > 0) {
            Log.e(
                    TAG,
                    "Notification icon of type " +
                            (isMipmap ? MIPMAP_TYPE : DRAWABLE_TYPE) +
                            " found: " +
                            resourceName
            );
            return resId;
          }
        }
      }
    } catch (PackageManager.NameNotFoundException e) {
      Log.e(TAG, "Failed to load meta-data, NameNotFound: " + e.getMessage());
    } catch (NullPointerException e) {
      Log.e(TAG, "Failed to load meta-data, NullPointer: " + e.getMessage());
    }
    return android.R.drawable.ic_media_play;
  }
}
