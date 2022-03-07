# Setup

Apologies I shall only cover **Mac** - One day I may include Linux and Windows.

Install [Homebrew](https://brew.sh) for easy package management on Mac:

```shell
ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
```

Installation essentials:

```shell
brew update
brew install --cask temurin
brew install scala
brew install sbt
brew install terraform
brew install cdktf
brew install awscli
brew install jq
brew install gnupg
brew tap anchore/grype
brew install grype
```

**Note that you will need JDK 11 or above.**

We'll go with the latest at this time, 17 (temurin):
```shell
jenv add /Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
```

The last installation is to scan for any project vulnerabilities:
```shell
grype dir:.
```

However, for this `sbt` project [sbt-dependency-check](https://github.com/albuch/sbt-dependency-check) is installed, so instead of relying on `grype` run:
```shell
sbt dependencyCheck
```

And you should double check for any secrets you may have checked into Git:
```shell
git secrets --scan
```