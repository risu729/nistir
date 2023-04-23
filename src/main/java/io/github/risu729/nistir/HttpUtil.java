/*
 * Copyright (c) 2023 Risu
 *
 *  This source code is licensed under the MIT license found in the
 *  LICENSE file in the root directory of this source tree.
 *
 */

package io.github.risu729.nistir;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

@UtilityClass
class HttpUtil {

  private final OkHttpClient HTTP_CLIENT = new OkHttpClient();

  @NotNull
  Document getJsoupDocument(@NotNull HttpUrl url) {
    return Jsoup.parse(getAsString(url));
  }

  @NotNull
  Path downloadFile(@NotNull HttpUrl url, @NotNull Path path) {
    try (var response = createCall(url).execute()) {
      return Files.writeString(path, checkNotNull(response.body()).string());
    } catch (SocketTimeoutException e) {
      return downloadFile(url, path);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @NotNull
  String getAsString(@NotNull HttpUrl url) {
    try (var response = createCall(url).execute()) {
      return checkNotNull(response.body()).string();
    } catch (SocketTimeoutException e) {
      return getAsString(url);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private @NotNull Call createCall(@NotNull HttpUrl url) {
    return HTTP_CLIENT.newCall(new Request.Builder().url(url).build());
  }
}
