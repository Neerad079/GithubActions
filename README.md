# 🚀 Spring Boot CI/CD with GitHub Actions & EC2

Automated deployment pipeline for a Spring Boot application using GitHub Actions, Docker, and an EC2 (or any VPS) instance. Every push to `main` triggers a build → deploy cycle that SSHs into the server and rebuilds the app via Docker Compose.

---

## 📋 Table of Contents

- [Architecture](#-architecture)
- [Prerequisites](#-prerequisites)
- [Project Structure](#-project-structure)
- [Setting Up Your Server (EC2 / VPS)](#-setting-up-your-server-ec2--vps)
- [Generating SSH Keys](#-generating-ssh-keys)
- [Configuring GitHub Secrets](#-configuring-github-secrets)
- [How the CI/CD Pipeline Works](#-how-the-cicd-pipeline-works)
- [Required Project Files](#-required-project-files)
- [Local Development](#-local-development)
- [Troubleshooting](#-troubleshooting)

---

## 🏗 Architecture

```
Developer pushes to main
        │
        ▼
┌─────────────────────────┐
│   GitHub Actions CI/CD  │
│                         │
│  1. Checkout code       │
│  2. Setup JDK 21        │
│  3. Build with Maven    │
│  4. SSH into EC2        │
│  5. git pull + rebuild  │
└───────────┬─────────────┘
            │ SSH
            ▼
┌─────────────────────────┐
│     EC2 / VPS Server    │
│                         │
│  Docker Compose builds  │
│  and runs the Spring    │
│  Boot app on port 8080  │
└─────────────────────────┘
```

---

## ✅ Prerequisites

- **Java 21** (for local development)
- **Docker & Docker Compose** (on the server)
- **Git** (on the server)
- **A GitHub account** with a repository
- **An EC2 instance or VPS** with a public IP

---

## 📁 Project Structure

```
GithubActions/
├── .github/
│   └── workflows/
│       └── deploy.yaml          # CI/CD pipeline definition
├── src/
│   ├── main/
│   │   ├── java/demo/GithubActions/
│   │   │   ├── GithubActionsApplication.java   # Spring Boot entry point
│   │   │   └── home.java                       # REST controller
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/demo/GithubActions/
│           └── GithubActionsApplicationTests.java
├── .mvn/wrapper/                # Maven wrapper config
├── Dockerfile                   # Multi-stage Docker build
├── compose.yaml                 # Docker Compose service definition
├── pom.xml                      # Maven dependencies & build config
├── mvnw                         # Maven wrapper (Linux/macOS)
├── mvnw.cmd                     # Maven wrapper (Windows)
├── .gitignore
├── .gitattributes
└── README.md
```

---

## 🖥 Setting Up Your Server (EC2 / VPS)

### Option A: AWS EC2 Instance

1. **Launch an EC2 Instance**
   - Go to [AWS Console → EC2 → Launch Instance](https://console.aws.amazon.com/ec2/)
   - Choose **Ubuntu 22.04 LTS** (or Amazon Linux 2023)
   - Instance type: **t2.micro** (free tier eligible) or **t3.small** for better performance
   - Create or select a **key pair** (`.pem` file) for initial access
   - Security Group: Allow inbound traffic on:
     - **Port 22** (SSH)
     - **Port 8080** (Application)
   - Launch the instance

2. **Connect to your instance**
   ```bash
   ssh -i your-key.pem ubuntu@<your-ec2-public-ip>
   ```

3. **Install Docker & Docker Compose**
   ```bash
   # Update packages
   sudo apt update && sudo apt upgrade -y

   # Install Docker
   sudo apt install -y docker.io docker-compose-v2

   # Add your user to the docker group (avoids needing sudo)
   sudo usermod -aG docker $USER

   # Log out and back in for group changes to take effect
   exit
   ```

4. **Clone the repository on the server**
   ```bash
   cd /root   # or /home/ubuntu, depending on your user
   git clone https://github.com/<your-username>/GithubActions.git
   ```

### Option B: Any VPS Provider

You can use any VPS provider like **DigitalOcean**, **Hetzner**, **Linode**, or **Vultr**:
- Spin up an Ubuntu 22.04 server
- Follow the same Docker installation steps above
- Make sure ports **22** and **8080** are open in your firewall

---

## 🔑 Generating SSH Keys

The GitHub Actions workflow uses SSH to connect to your server. You need to generate a dedicated key pair for this.

### Step 1: Generate the Key Pair

Run this **on your local machine** (not the server):

```bash
ssh-keygen -t rsa -b 4096 -C "github-actions-deploy" -f ./deploy_key -N ""
```

This creates two files:
- `deploy_key` — **Private key** (goes into GitHub Secrets)
- `deploy_key.pub` — **Public key** (goes on the server)

### Step 2: Add the Public Key to Your Server

Copy the public key to your server's authorized keys:

```bash
# Option 1: Using ssh-copy-id
ssh-copy-id -i deploy_key.pub root@<your-server-ip>

# Option 2: Manually
cat deploy_key.pub | ssh root@<your-server-ip> "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```

### Step 3: Test the Connection

```bash
ssh -i deploy_key root@<your-server-ip>
```

If you can log in without a password prompt, the key is set up correctly.

> ⚠️ **Important:** Never commit your private key to the repository. Add it to `.gitignore` and store it only in GitHub Secrets.

---

## 🔒 Configuring GitHub Secrets

The workflow uses three secrets that you need to configure in your GitHub repository.

### How to Add Secrets

1. Go to your repo on GitHub
2. Navigate to **Settings → Secrets and variables → Actions**
3. Click **"New repository secret"** for each of the following:

| Secret Name    | Value                                                         | Example                          |
|----------------|---------------------------------------------------------------|----------------------------------|
| `SSH_HOST`     | Your server's public IP address                               | `54.123.45.67`                   |
| `SSH_USERNAME` | The SSH username to connect with                              | `root` or `ubuntu`               |
| `SSH_KEY`      | The **entire contents** of your private key file (`deploy_key`) | Copy-paste the full file contents including `-----BEGIN OPENSSH PRIVATE KEY-----` and `-----END OPENSSH PRIVATE KEY-----` |

### Getting the Private Key Content

```bash
cat deploy_key
```

Copy the **entire output** (including the BEGIN/END lines) and paste it as the value for `SSH_KEY`.

---

## ⚙ How the CI/CD Pipeline Works

The pipeline is defined in [`.github/workflows/deploy.yaml`](.github/workflows/deploy.yaml) and runs on every push to `main`.

### Job 1: `build-and-test`

```yaml
build-and-test:
  runs-on: ubuntu-latest
  steps:
    - Checkout code
    - Set up JDK 21 (Temurin distribution, with Maven caching)
    - Run: chmod +x mvnw && ./mvnw verify -DskipTests
```

- Compiles the Java source code and packages it into a JAR
- Maven dependencies are cached for faster subsequent builds
- `chmod +x mvnw` is needed because Windows doesn't preserve Linux execute permissions
- Tests are skipped in CI (`-DskipTests`) since the default `@SpringBootTest` requires Docker Compose

### Job 2: `deploy` (runs only if build passes)

```yaml
deploy:
  runs-on: ubuntu-latest
  needs: build-and-test
  steps:
    - Checkout code
    - SSH into the server using appleboy/ssh-action
    - Run: git pull → docker compose up -d --build
```

- Uses [appleboy/ssh-action](https://github.com/appleboy/ssh-action) to SSH into your server
- Pulls the latest code from the repository
- Rebuilds and restarts the Docker container with the new code
- `set -e` ensures the script stops immediately if any command fails

---

## 📄 Required Project Files

These are the essential files needed for the CI/CD pipeline to work:

### 1. `Dockerfile` — Multi-stage Docker Build

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline
COPY src/ src/
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- **Stage 1 (build):** Compiles the application using the full JDK
- **Stage 2 (runtime):** Uses the lightweight JRE-only image to run the JAR

### 2. `compose.yaml` — Docker Compose Service

```yaml
services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    restart: unless-stopped
    ports:
      - "8080:8080"
```

### 3. `.github/workflows/deploy.yaml` — GitHub Actions Workflow

See the [pipeline section](#-how-the-cicd-pipeline-works) above for a full breakdown.

---

## 💻 Local Development

### Run Locally (without Docker)

```bash
# Linux / macOS
chmod +x mvnw
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

### Run Locally (with Docker)

```bash
docker compose up --build
```

The application will be available at **http://localhost:8080**

---

## 🔧 Troubleshooting

### Build fails with "Permission denied: ./mvnw"

The Maven wrapper doesn't have execute permission. The workflow already handles this with `chmod +x mvnw`, but if running locally on Linux/macOS:

```bash
chmod +x mvnw
```

### SSH connection fails in GitHub Actions

- Verify your secrets are correctly set in **Settings → Secrets → Actions**
- Make sure the public key is in `~/.ssh/authorized_keys` on the server
- Check that port 22 is open in your server's security group / firewall
- Test SSH locally first: `ssh -i deploy_key <username>@<host>`

### Docker build fails on the server

- Ensure Docker is installed: `docker --version`
- Ensure Docker Compose v2 is installed: `docker compose version`
- Check available disk space: `df -h`
- View container logs: `docker compose logs -f`

### `git pull` fails on the server

- If using HTTPS, set up a credential helper or personal access token
- If using SSH, ensure the server has a deploy key configured for the GitHub repo
- Check that the repo is cloned in the correct path (`/root/GithubActions`)

---

## 📝 License

This project is for learning and demonstration purposes.
