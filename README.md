# Pre requisite setup for orchestration ECS tasks with AWS Step Function

This project will create the following AWS services:
*  VPC with 2 subnets
*  2 AWS S3 buckets
    * First bucket is the ‘pre-processing-bucket’
    * Second bucket is the ‘main-processing-bucket’
*	AWS ECS Cluster
*	AWS ECS task definitions. The container image used in task definition are already created and available in public AWS ECR repository. The source code and steps to build the image are available in [GitHub](https://github.com/abhishekjawali/StepFunctionECSTasks). 
    * First task definition is for ‘pre-processing-task’ ECS Task. This uses the container image from ```“public.ecr.aws/abhishekjv/pre-processing-service:latest”```
    * Second task definition is for ‘main-processing-task’ ECS Task. This uses the container image from ```“public.ecr.aws/abhishekjv/main-processing-service:latest”```
*   IAM Roles
    * IAM Role required for Step Function execution
    * IAM Role required for ECS Task execution

## Setup 

### Pre requisites
-   An AWS account
-   [AWS CLI version 2](https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2.html)
-   [AWS CDK CLI](https://docs.aws.amazon.com/cdk/latest/guide/cli.html)
-   [Java 11](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html)

### Build the infrastructure
```
git clone https://github.com/abhishekjawali/StepFunctionECSPreReq.git
cd StepFunctionECSPreReq
cdk synth
cdk deploy
```