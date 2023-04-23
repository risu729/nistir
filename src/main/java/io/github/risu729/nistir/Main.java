/*
 * Copyright (c) 2023 Risu
 *
 *  This source code is licensed under the MIT license found in the
 *  LICENSE file in the root directory of this source tree.
 *
 */

package io.github.risu729.nistir;

import com.google.gson.GsonBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriterBuilder;
import com.opencsv.exceptions.CsvException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.experimental.UtilityClass;
import okhttp3.HttpUrl;

@UtilityClass
class Main {

  final Path RESULTS_DIR = Path.of("results");

  public void main(String[] args) throws IOException, CsvException {

    Files.createDirectories(RESULTS_DIR.resolve("structures"));
    List<String[]> input;
    try (var reader =
        new CSVReader(Files.newBufferedReader(Path.of("src", "main", "resources", "input.csv")))) {
      input = reader.readAll();
    }
    var inputHeader = List.of(input.get(0));
    var nameIndex = inputHeader.indexOf("Name");
    var formulaIndex = inputHeader.indexOf("Formula");

    var result =
        input.stream()
            .skip(1)
            .<Map.Entry<String[], List<SpeciesData>>>mapMulti(
                (row, consumer) -> {
                  var name = row[nameIndex];
                  var data = Nist.getSpeciesDataByFormula(row[formulaIndex]);
                  if (data.isEmpty()) {
                    data = Nist.getSpeciesDataByName(name);
                  }
                  if (data.isEmpty()) {
                    System.out.printf("No data found for %s.%n", name);
                    return;
                  }
                  System.out.printf("Found %d data for %s.%n", data.size(), name);
                  consumer.accept(Map.entry(row, data));
                })
            .toList();

    var resultPath = RESULTS_DIR.resolve("result.csv");
    try (var writer = new CSVWriterBuilder(Files.newBufferedWriter(resultPath)).build()) {
      writer.writeNext(
          new String[] {
            "Input Name",
            "Input Formula",
            "NIST Name",
            "NIST Formula",
            "Molecular Weight",
            "InChI",
            "CAS Registry Number",
            "Structure Image",
            "IR Spectrum Peak Wavenumbers / cm^-1"
          });
      result.forEach(
          entry ->
              entry.getValue().stream()
                  .map(
                      data -> {
                        List<String> row = new ArrayList<>(Arrays.asList(entry.getKey()));
                        row.add(data.name());
                        row.add(data.formula());
                        row.add(String.valueOf(data.molecularWeight()));
                        row.add(data.inChI());
                        row.add(data.casRegistryNumber());
                        row.add(data.structureImageUrl().toString());
                        row.addAll(
                            data.irSpectrumPeakWavenumbers().stream()
                                .map(String::valueOf)
                                .toList());
                        return row;
                      })
                  .map(row -> row.toArray(String[]::new))
                  .forEach(writer::writeNext));
    }
    System.out.printf("Results are written in %s.%n", resultPath);

    System.out.printf(
        "Full results are written as json in %s.%n",
        Files.writeString(
            RESULTS_DIR.resolve("result.json"),
            new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .registerTypeHierarchyAdapter(
                    Path.class,
                    TypeAdapters.create(
                        path ->
                            StreamSupport.stream(path.spliterator(), false)
                                .map(Path::toString)
                                .collect(Collectors.joining("/")),
                        Path::of))
                .registerTypeHierarchyAdapter(HttpUrl.class, TypeAdapters.create(HttpUrl::parse))
                .create()
                .toJson(result.stream().map(Map.Entry::getValue).toList())));

    System.out.printf("Citations are written in %s.%n", Nist.writeCitations());
  }
}
