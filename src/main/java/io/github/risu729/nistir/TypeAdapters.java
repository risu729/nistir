/*
 * Copyright (c) 2023 Risu
 *
 *  This source code is licensed under the MIT license found in the
 *  LICENSE file in the root directory of this source tree.
 *
 */

package io.github.risu729.nistir;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Function;

@UtilityClass
class TypeAdapters {

  <T> @NotNull TypeAdapter<T> create(@NotNull Function<? super String, ? extends T> reader) {
    return create(T::toString, reader);
  }

  <T> @NotNull TypeAdapter<T> create(@NotNull Function<? super T, String> writer,
      @NotNull Function<? super String, ? extends T> reader) {
    return new TypeAdapter<T>() {
      @Override
      public void write(@NotNull JsonWriter out, @NotNull T value) throws IOException {
        out.value(writer.apply(value));
      }

      @Override
      public T read(@NotNull JsonReader in) throws IOException {
        return reader.apply(in.nextString());
      }
    }.nullSafe();
  }
}
