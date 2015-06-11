package com.kyper;

import com.wordnik.swagger.codegen.SupportingFile;
import com.wordnik.swagger.codegen.languages.PythonClientCodegen;

public class MyPythonCodeGenerator extends PythonClientCodegen {

    public MyPythonCodeGenerator() {
        super();

        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("api-doc.mustache", apiPackage, "api-doc.json"));
    }
}
