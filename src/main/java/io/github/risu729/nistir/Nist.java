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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

@UtilityClass
class Nist {

  private final HttpUrl NIST_URL = checkNotNull(HttpUrl.parse("https://webbook.nist.gov/"));
  private final HttpUrl NIST_SEARCH_URL =
      checkNotNull(NIST_URL.resolve("/cgi/cbook.cgi?MatchIso=on&NoIon=on&Units=SI&cIR=on"));

  private final List<String> CITATION_URLS = new ArrayList<>();

  @NotNull
  @Unmodifiable
  List<@NotNull SpeciesData> getSpeciesDataByFormula(@NotNull String formula) {
    // only number of elements are supported
    return getSpeciesData("Formula", formula.replaceAll("cyc|\\([EZ]\\)|trans|[-= ]|^[nc]]", ""));
  }

  @NotNull
  @Unmodifiable
  List<@NotNull SpeciesData> getSpeciesDataByName(@NotNull String name) {
    return getSpeciesData("Name", name);
  }

  @NotNull
  Path writeCitations() throws IOException {
    return Files.writeString(
        Main.RESULTS_DIR.resolve("citations.txt"),
        CITATION_URLS.stream()
            .map(NIST_URL::resolve)
            .filter(Objects::nonNull)
            .map(HttpUtil::getJsoupDocument)
            .map(Document::body)
            .map(body -> body.select("p:contains(The citation for data from) + p.indented"))
            .flatMap(List::stream)
            .map(Element::text)
            .collect(Collectors.joining(System.getProperty("line.separator"))));
  }

  private @NotNull @Unmodifiable List<@NotNull SpeciesData> getSpeciesData(
      @NotNull String queryName, @NotNull String queryValue) {
    var pages =
        getPages(NIST_SEARCH_URL.newBuilder().addQueryParameter(queryName, queryValue).build());
    List<SpeciesData> speciesData = new ArrayList<>();
    for (var page : pages.entrySet()) {

      var body = page.getValue().body();
      var name = body.select("h1#Top").text();
      var inChI =
          body.select("h1#Top + ul > li:contains(IUPAC Standard InChI) > div > div > span")
              .text()
              .replaceFirst("InChI=", "");

      var formula =
          body.select("h1#Top + ul > li:contains(%s)".formatted("Formula"))
              .text()
              .replaceFirst("^.+: ", "");
      var molecularWeight =
          Double.parseDouble(
              body.select("h1#Top + ul > li:contains(Molecular weight)")
                  .text()
                  .replaceFirst("^.+: ", ""));
      var casRegistryNumber =
          body.select("h1#Top + ul > li:contains(CAS Registry Number)")
              .text()
              .replaceFirst("^.+: ", "");
      var structureImageUrl =
          checkNotNull(
              NIST_URL.resolve(
                  body.select("h1#Top + ul > li:contains(Chemical structure) > img[src]")
                      .attr("src")));
      var structureImage =
          HttpUtil.downloadFile(
              structureImageUrl,
              Main.RESULTS_DIR
                  .resolve("structures")
                  .resolve(
                      "%s_%s.png"
                          .formatted(name.replaceAll(" ", ""), inChI.replaceAll("/", ""))));
      var url = page.getKey();
      var peekWavenumbers = getIrSpectrumPeekWavenumbers(body);

      if (peekWavenumbers.isEmpty()) {
        continue;
      }

      speciesData.add(
          new SpeciesData(
              name,
              formula,
              molecularWeight,
              inChI,
              casRegistryNumber,
              structureImageUrl,
              structureImage,
              url,
              peekWavenumbers));
    }
    return Collections.unmodifiableList(speciesData);
  }

  private @NotNull @Unmodifiable List<@NotNull Long> getIrSpectrumPeekWavenumbers(
      @NotNull Element body) {
    var spectrumUrls =
        body.select("p.section-head:contains(Data compiled by:) + ul > li").stream()
            .map(element -> element.select("a[href]").last()) // get the largest resolution
            .filter(Objects::nonNull)
            .map(element -> element.attr("href"))
            .filter(Predicate.not(String::isBlank))
            .map(NIST_URL::resolve)
            .toList();

    Stream<Element> bodies;
    if (spectrumUrls.isEmpty()) {
      bodies = Stream.of(body);
    } else {
      bodies = spectrumUrls.stream().map(HttpUtil::getJsoupDocument).map(Document::body);
    }
    return bodies
        .map(Nist::getIrSpectrumPeekWavenumber)
        .flatMapToLong(OptionalLong::stream)
        .boxed()
        .toList();
  }

  private @NotNull OptionalLong getIrSpectrumPeekWavenumber(@NotNull Element body) {

    // only include gas phase, not for solid or liquid
    var jdxUrl =
        body.select("h3:contains(Gas Phase) ~ div.indented > p:contains(JCAMP-DX format) > a[href]")
            .attr("href");
    if (jdxUrl.isBlank()) {
      return OptionalLong.empty();
    }

    var citationUrl =
        body.select("p.section-head:contains(Data compiled by:) > a[href]").attr("href");
    if (!CITATION_URLS.contains(citationUrl)) {
      CITATION_URLS.add(citationUrl);
    }

    return OptionalLong.of(
        JcampDx.getPeekWavenumber(HttpUtil.getAsString(checkNotNull(NIST_URL.resolve(jdxUrl)))));
  }

  private @NotNull @Unmodifiable Map<@NotNull HttpUrl, @NotNull Document> getPages(
      @NotNull HttpUrl url) {

    var document = HttpUtil.getJsoupDocument(url);

    var title = document.title();
    if (title.contains("Not Found") || title.contains("Unable to Perform Search")) {
      return Map.of();
    } else if (title.equals("Search Results")) {
      return document
          .body()
          .select("p:contains(Click on the name to see more data.) + ol > li > a[href]")
          .stream()
          .map(Element::attributes)
          .map(attributes -> attributes.get("href"))
          .map(NIST_URL::resolve)
          .filter(Objects::nonNull)
          .map(Nist::getPages)
          .map(Map::entrySet)
          .flatMap(Set::stream)
          .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    } else {
      return Map.of(url, document);
    }
  }
}
