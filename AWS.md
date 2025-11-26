AWS Linux 2023 - 32GB
t3.medium
외부 접속 포트 허용
IAM 권한 부여 - Labinstance

# 시스템 업데이트
sudo yum update -y

# Docker 설치
sudo yum install docker -y
sudo systemctl start docker
sudo systemctl enable docker
sudo usermod -aG docker $USER

#Buildx 설치
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -L "https://github.com/docker/buildx/releases/download/v0.17.0/buildx-v0.17.0.linux-amd64" -o /usr/local/lib/docker/cli-plugins/docker-buildx
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx

# Docker Compose 설치
sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
sudo chmod +x /usr/local/bin/docker-compose

# Git 설치
sudo yum install git -y

# 재접속
exit

cd ~
git clone https://github.com/SolidCitadel/UniSync.git
cd UniSync

# 권한 추가
whoami
>>> ssm-user
sudo usermod -aG docker ssm-user

exit

groups
>>> ssm-user docker

# .env.local.mine 내용 복붙
cd ~/UniSync
nano .env.local

# 스크립트 파일들에 실행권한 부여
chmod +x localstack-init/*.sh
# 확인
ls -la localstack-init/
chmod +x scripts/wait-for-localstack.sh

# 실행
docker-compose -f docker-compose.demo.yml up -d --build

# 컨테이너 확인
docker-compose -f docker-compose.demo.yml ps -a