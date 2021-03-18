package com.myorg;

import software.amazon.awscdk.core.App;

public class InfraApp {
    public static void main(final String[] args) {
        App app = new App();

        new InfraStack(app, "InfraStack");

        app.synth();
    }
}
