/*
 * Copyright (c) 2023 Risu
 *
 *  This source code is licensed under the MIT license found in the
 *  LICENSE file in the root directory of this source tree.
 *
 */

package io.github.risu729.nistir;

import java.nio.file.Path;
import java.util.List;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

record SpeciesData(
    @NotNull String name,
    @NotNull String formula,
    double molecularWeight,
    @NotNull String inChI,
    @NotNull String casRegistryNumber,
    @NotNull HttpUrl structureImageUrl,
    @NotNull Path structureImage,
    @NotNull HttpUrl url,
    @NotNull List<@NotNull Long> irSpectrumPeakWavenumbers) {

  @Override
  public int hashCode() {
    return inChI.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof SpeciesData data && inChI.equals(data.inChI());
  }
}
