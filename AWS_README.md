# Setting up PAS in AWS

## Creating the Instance

The first step in setting up Prior Authorization RI in AWS is to create the AWS instance.

1.  Log into console.aws.amazon.com using the login created by the PAS Admin. To request an account email blangley@mitre.org.
2.  Launch a new EC2 instance by selecting EC2 service. On the left nav bar under Instance select Instance. Click Launch Instance.
    ![LaunchInstance](/documentation/Launch_Instance.png)
3.  Select Ubuntu Server 18.04
4.  Select the appropriate instance type (t2.micro is fine for testing) and click next.
5.  On the Configure Instance Details ensure the following is set:
    1. Network: preopopulated with Generic VPC
    2. Subnet: select any Generic MITRE-Only Public Subnet (us-east-1a is appropriate)
    3. Auto-assign Public IP: Enable
       ![ConfigureInstance](/documentation/Configure_Instance.png)
6.  Continue through the next screens until Configure Security Group
7.  Add the existing security groups `MITRE Baseline`, `MITRE Web`, and `WARP`.
    ![ConfigureSecurity](/documentation/Configure_Security_Group.png)
8.  After clicking Review and Launch you will be prompted for access keys. Unless creating an instance for private testing select the existing keys `PAS_Team.pem`.
    Note: These keys will be needed to SSH into the VM. You will be unable to access the instance without the keys. When sharing keys with other team members always send over an encrypted channel.

### Important Instance Details

Once the instance has launch successfully important details can be found by clicking on the instance in the list of active ones. Some of the important properties to note are Instance ID, Security Groups, and Public DNS IPv4.
![InstanceDetails](/documentation/Instance_Details.png)
The Public DNS IPv4 is the URL used to SSH into the VM and as the base of the service (rest-root). In the instance above the Public DNS IPv4 is `ec2-54-152-51-3.compute-1.amazonaws.com`. To SSH into this VM the command would be

```bash
$ ssh -i "PAS_Team.pem" ubuntu@ec2-54-152-51-3.compute-1.amazonaws.com
```

### Troubleshooting

1.  Verify you are using US-EAST-1 (N. Virginia)
    ![Verify](/documentation/Verify_USEAST_1.png)

## Running PAS

Once the VM is up and running (this may take a few minutes), you deploy the Prior Authorization service. To get PAS up and running on SSH into the VM, download the setup script, and then execute as root user.

```bash
$ ssh -i “PAS_Team.pem” ubuntu@{Public DNS (IPv4)}
$ wget https://raw.githubusercontent.com/HL7-DaVinci/prior-auth/aws-docs/pas_setup.sh
$ sudo ./pas_setup.sh
```

The setup script will install Docker and run the service in a new container. By default PAS runs on port 9000, however the AWS sandbox does not expose this port by default. The service will now be running on `{Public DNS IPv4}:8080` in debug mode.

### Running Without Docker

The script above will install Docker and then run the service through a Docker container. To test the service on the VM without using Docker you must install Gradle and Java 8 manually:

```bash
$ sudo apt-install -y gradle
$ sudo apt-get install -y openjdk-8-jre
```

And then run:

```bash
$ sudo gradle bootRun
```

### Troubleshooting

1. A `permissions denied` error while attempting to run the set up script is mostly likely because of Ubuntu file permissions. Update the file to have execute permissions for your user. To give read, write, and execute permissions to all users run:

```bash
$ chmod 777 pas_setup.sh
```

2. By default the setup script runs the service in debug mode. To prevent this either change the last line of the script or run directly from command line:

```bash
$ docker run -d -p 8080:9000 -it --rm --name davinci-prior-auth hspc/davinci-prior-auth:latest
```

3. Unable to download the setup script. Create a file called `pas_setup.sh` and add the following:

```bash
apt update -y
apt install -y apt-transport-https ca-certificates curl software-properties-common
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -
add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/ubuntu bionic stable"
apt update -y
apt-cache policy docker-ce
apt install -y docker-ce
git clone https://github.com/HL7-DaVinci/prior-auth.git
cd prior-auth
docker build -t hspc/davinci-prior-auth:latest .
docker run -p 8080:9000 -e debug='true' -it --rm --name davinci-prior-auth hspc/davinci-prior-auth:latest
```
