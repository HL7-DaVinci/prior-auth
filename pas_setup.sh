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
docker run -d -p 8080:9000 -e debug='true' -it --rm --name davinci-prior-auth hspc/davinci-prior-auth:latest
