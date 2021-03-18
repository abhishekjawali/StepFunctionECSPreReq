package com.myorg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.CfnOutputProps;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.FargateTaskDefinitionProps;
import software.amazon.awscdk.services.ecs.LogDrivers;
import software.amazon.awscdk.services.ecs.TaskDefinition;
import software.amazon.awscdk.services.events.targets.EcsTask;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.IManagedPolicy;
import software.amazon.awscdk.services.iam.Policy;
import software.amazon.awscdk.services.iam.PolicyDocument;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.PolicyStatementProps;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.s3.Bucket;

public class InfraStack extends Stack {
        public InfraStack(final Construct scope, final String id) {
                this(scope, id, null);
        }

        public InfraStack(final Construct scope, final String id, final StackProps props) {
                super(scope, id, props);
                
                // Create VPC
                Vpc vpc = Vpc.Builder.create(this, "MyVpc").maxAzs(3).build();

                // Create ECS Cluster
                Cluster cluster = Cluster.Builder.create(this, "MyCluster").vpc(vpc).build();

                // Create 2 S3 buckets
                String accountId = this.getAccount().toString();// Aws.ACCOUNT_ID.toString();

                // String accountId = "abhi-23434534r3463twf";
                String preProcessingBucketName = accountId.concat("-").concat("pre-processing-bucket");
                String mainBucketName = accountId.concat("-").concat("main-processing-bucket");
                // Bucket preProcessingBucket = new Bucket(this, preProcessingBucketName);
                Bucket preProcessingBucket = Bucket.Builder.create(this, "pre-processing-bucket")
                                .bucketName(preProcessingBucketName).autoDeleteObjects(true)
                                .removalPolicy(RemovalPolicy.DESTROY).build();
                // Bucket mainBucket = new Bucket(this, mainBucketName);
                Bucket mainBucket = Bucket.Builder.create(this, "main-bucket").bucketName(mainBucketName)
                                .autoDeleteObjects(true).removalPolicy(RemovalPolicy.DESTROY).build();

                // Configure IAM Roles needed for ECS. As part of this demo, the IAM role should
                // have the following policies attached to it:
                // 1. ECS Task execution role policy
                // 2. S3 put object and get object
                // 3. ECR repo pull policy

                List<String> s3PolicyActionsList = new ArrayList<String>();
                s3PolicyActionsList.add("s3:PutObject");
                s3PolicyActionsList.add("s3:Get*");
                s3PolicyActionsList.add("s3:List*");
                // s3PolicyActionsList.add("GetBucketOwnershipControls")

                List<String> s3ResourceList = new ArrayList<String>();
                s3ResourceList.add(preProcessingBucket.getBucketArn());
                s3ResourceList.add(mainBucket.getBucketArn());

                PolicyDocument s3PolicyDocument = new PolicyDocument();
                s3PolicyDocument.addStatements(new PolicyStatement(PolicyStatementProps.builder().effect(Effect.ALLOW)
                                .actions(s3PolicyActionsList).resources(s3ResourceList).build()));
                Policy ecsS3Policy = Policy.Builder.create(this, "ECSS3Policy").document(s3PolicyDocument).build();

                Role infraECSExecutionRole = new Role(this, "StepFunctionECSTaskExecutionRole",
                                RoleProps.builder().assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
                                                .roleName("StepFunctionECSTaskExecutionRole").build());

                infraECSExecutionRole.addManagedPolicy(new IManagedPolicy() {

                        @Override
                        public @NotNull String getManagedPolicyArn() {
                                return "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy";
                        }

                });

                infraECSExecutionRole.addManagedPolicy(new IManagedPolicy() {

                        @Override
                        public @NotNull String getManagedPolicyArn() {
                                return "arn:aws:iam::aws:policy/AmazonS3FullAccess";
                        }

                });

                // ecsS3Policy.attachToRole(infraECSExecutionRole);

                FargateTaskDefinitionProps fargateTaskDefinitionProps = FargateTaskDefinitionProps.builder()
                                .taskRole(infraECSExecutionRole).executionRole(infraECSExecutionRole).build();

                Map<String, String> crudFunctionEnvironment = new HashMap<String, String>();
                crudFunctionEnvironment.put("BUCKET_NAME", mainBucket.getBucketName());
                crudFunctionEnvironment.put("AWS_REGION", "");

                TaskDefinition crudServiceTaskDefinition = new FargateTaskDefinition(this, "CrudServiceTaskDef",
                                fargateTaskDefinitionProps);
                crudServiceTaskDefinition.addContainer("DefaultContainer",
                                ContainerDefinitionOptions.builder().image(ContainerImage
                                                .fromRegistry("public.ecr.aws/e5n9d9z4/abhishekjv/crud-service:latest"))
                                                .memoryLimitMiB(512).environment(crudFunctionEnvironment)
                                                .logging(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                                                                .streamPrefix("ecs-step-function-crud-service")
                                                                .build()))
                                                .build());

                Map<String, String> preProcessingFunctionEnvironment = new HashMap<String, String>();
                preProcessingFunctionEnvironment.put("SOURCE_BUCKET_NAME", preProcessingBucket.getBucketName());
                preProcessingFunctionEnvironment.put("DESTINATION_BUCKET_NAME", mainBucket.getBucketName());
                preProcessingFunctionEnvironment.put("AWS_REGION", "");

                // FargateTaskDefinitionProps fargateTaskDefinitionProps =
                // FargateTaskDefinitionProps.builder().taskRole(infraECSExecutionRole).executionRole(infraECSExecutionRole).build();
                TaskDefinition preProcessingServiceTaskDefinition = new FargateTaskDefinition(this,
                                "PreProcessingTaskDef", fargateTaskDefinitionProps);
                preProcessingServiceTaskDefinition.addContainer("DefaultContainer", ContainerDefinitionOptions.builder()
                                .image(ContainerImage.fromRegistry(
                                                "public.ecr.aws/e5n9d9z4/abhishekjv/preprocessing-service:latest"))
                                .memoryLimitMiB(512).environment(preProcessingFunctionEnvironment)
                                .logging(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                                                .streamPrefix("ecs-step-function-preprocessing-service").build()))
                                .build());

                EcsTask ecsTask = EcsTask.Builder.create().cluster(cluster)
                                .taskDefinition(preProcessingServiceTaskDefinition)
                                .taskDefinition(crudServiceTaskDefinition).role(infraECSExecutionRole).build();

                // Step functions
                // Create IAM role required by step function
                Role stepFunctionExecutionRole = new Role(this, "StepFunctionExecutionRole",
                                RoleProps.builder().assumedBy(new ServicePrincipal("states.amazonaws.com"))
                                                .roleName("StepFunctionECSOrchestratorExecutionRole").build());

                List<String> stepFunctionToECSActionsList = new ArrayList<String>();
                stepFunctionToECSActionsList.add("iam:GetRole");
                stepFunctionToECSActionsList.add("iam:PassRole");
                stepFunctionToECSActionsList.add("events:DescribeRule");
                stepFunctionToECSActionsList.add("events:PutTargets");
                stepFunctionToECSActionsList.add("events:PutRule");
                stepFunctionToECSActionsList.add("ecs:RunTask");
                stepFunctionToECSActionsList.add("ecs:StopTask");
                stepFunctionToECSActionsList.add("ecs:DescribeTasks");

                List<String> stepFunctionToECSResourceList = new ArrayList<String>();
                stepFunctionToECSResourceList.add(infraECSExecutionRole.getRoleArn());
                stepFunctionToECSResourceList.add(preProcessingServiceTaskDefinition.getTaskDefinitionArn());
                stepFunctionToECSResourceList.add(crudServiceTaskDefinition.getTaskDefinitionArn());
                stepFunctionToECSResourceList
                                .add("arn:aws:events:us-east-1:885629272022:rule/StepFunctionsGetEventsForECSTaskRule");// ToDo:
                                                                                                                        // add
                                                                                                                        // events
                                                                                                                        // task
                                                                                                                        // rule

                PolicyDocument stepFunctionToECSPolicyDocument = new PolicyDocument();
                stepFunctionToECSPolicyDocument.addStatements(new PolicyStatement(PolicyStatementProps.builder()
                                .effect(Effect.ALLOW).actions(stepFunctionToECSActionsList)
                                .resources(stepFunctionToECSResourceList).build()));
                Policy stepFunctionToECSPolicy = Policy.Builder.create(this, "StepFunctionToECSPolicy")
                                .document(stepFunctionToECSPolicyDocument).policyName("StepFunctionToECSPolicy")
                                .build();
                stepFunctionToECSPolicy.attachToRole(stepFunctionExecutionRole);

                List<String> xRayActionList = new ArrayList<String>();
                xRayActionList.add("xray:PutTraceSegments");
                xRayActionList.add("xray:PutTelemetryRecords");
                xRayActionList.add("xray:GetSamplingRules");
                xRayActionList.add("xray:GetSamplingTargets");

                List<String> xRayResourceList = new ArrayList<String>();
                xRayResourceList.add("*");
                PolicyDocument stepFunctionToXrayPolicyDocument = new PolicyDocument();
                stepFunctionToXrayPolicyDocument.addStatements(new PolicyStatement(PolicyStatementProps.builder()
                                .effect(Effect.ALLOW).actions(xRayActionList).resources(xRayResourceList).build()));
                Policy stepFunctionToXrayPolicy = Policy.Builder.create(this, "StepFunctionToXrayPolicy")
                                .document(stepFunctionToXrayPolicyDocument).policyName("StepFunctionToXrayPolicy")
                                .build();
                stepFunctionToXrayPolicy.attachToRole(stepFunctionExecutionRole);

                // CloudTrail
                // TODO: This is manual step

                // EventBridge

                CfnOutput stepFunctionRle = new CfnOutput(this, "Step Function Execution ROle",
                                CfnOutputProps.builder().value(stepFunctionExecutionRole.getRoleArn())
                                                .exportName("StepFunctionExcutionRole").build());

                CfnOutput preProcessingTaskDefinition = new CfnOutput(this, "preProcessingTaskDefinition",
                                CfnOutputProps.builder()
                                                .value(preProcessingServiceTaskDefinition.getTaskDefinitionArn())
                                                .exportName("preProcessingTaskDefinition").build());

                CfnOutput mainTaskDefinition = new CfnOutput(this, "mainTaskDefinition",
                                CfnOutputProps.builder().value(crudServiceTaskDefinition.getTaskDefinitionArn())
                                                .exportName("mainTaskDefinition").build());

                CfnOutput ecsClusterName = new CfnOutput(this, "ecsClusterName", CfnOutputProps.builder()
                                .value(cluster.getClusterName()).exportName("ecsClusterName").build());

                CfnOutput defaultSG = new CfnOutput(this, "defaultSG", CfnOutputProps.builder()
                                .value(vpc.getVpcDefaultSecurityGroup()).exportName("defaultSG").build());

                CfnOutput subnetId = new CfnOutput(this, "subnetId", CfnOutputProps.builder()
                                .value(vpc.getPublicSubnets().get(0).getSubnetId()).exportName("subnetId").build());

                CfnOutput preProcessingS3Bucket = new CfnOutput(this, "preProcessingS3Bucket",
                                CfnOutputProps.builder().value(preProcessingBucket.getBucketName())
                                                .exportName("preProcessingS3Bucket").build());

                CfnOutput mainS3Bucket = new CfnOutput(this, "mainS3Bucket", CfnOutputProps.builder()
                                .value(mainBucket.getBucketName()).exportName("mainS3Bucket").build());

        }
}
