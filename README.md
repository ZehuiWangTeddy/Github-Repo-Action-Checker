# Github-Repo-Action-Checker
This is the command line tool to check GitHub workflow runs, the solution of task 2 from Third-party CI/CD systems representation in TeamCity.

## How to run this client
1. Switch to project folder
``
 cd GithubCheck
``

2. Build project

``
 mvn clean package
``
3. Run project

``
   java -jar target/GithubCheck-1.0-SNAPSHOT.jar -r owner/repo -t <token> -i <interval time>
``