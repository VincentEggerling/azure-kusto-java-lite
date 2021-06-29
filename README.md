# A Lite version of the Microsoft Azure Kusto (Azure Data Explorer) SDK for Java.

The goal of this library is remove any network interaction with the Azure API. We kept only the objects 
and methods to generate the request JSON body and to parse the JSON reponse.


To build this library, you have to fetch this repo in a Bazel framework and write a BUILD file that could look like:
```
java_library(
    name = "azure-kusto-java-lite",
    visibility = ["//visibility:public"],
    srcs = glob(["data/src/main/java/com/microsoft/azure/kusto/data/**/*.java"]),
    deps = [
        "//org/json:json",
        "//org/apache/commons:commons_lang3",
    ]
)
```

