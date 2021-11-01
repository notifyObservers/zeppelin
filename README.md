# Apache Zeppelin

**Documentation:** [User Guide](https://zeppelin.apache.org/docs/latest/index.html)<br/>
**Mailing Lists:** [User and Dev mailing list](https://zeppelin.apache.org/community.html)<br/>
**Continuous Integration:** ![core](https://github.com/apache/zeppelin/workflows/core/badge.svg) ![frontend](https://github.com/apache/zeppelin/workflows/frontend/badge.svg) ![rat](https://github.com/apache/zeppelin/workflows/rat/badge.svg) <br/>
**Contributing:** [Contribution Guide](https://zeppelin.apache.org/contribution/contributions.html)<br/>
**Issue Tracker:** [Jira](https://issues.apache.org/jira/browse/ZEPPELIN)<br/>
**License:** [Apache 2.0](https://github.com/apache/zeppelin/blob/master/LICENSE)


**Zeppelin**, a web-based notebook that enables interactive data analytics. You can make beautiful data-driven, interactive and collaborative documents with SQL, Scala and more.

Core features:
   * Web based notebook style editor.
   * Built-in Apache Spark support


To know more about Zeppelin, visit our web site [https://zeppelin.apache.org](https://zeppelin.apache.org)

# Flink on Zeppelin
## Getting Started
1. Download source code to your local repository
2. cd into your repository, ${zeppelin_home} and do "kubectl -f apply k8s/zeppelin-server.yaml"
3. Run "kubectl get pods", you should see a running zeppelin-server container
4. Run "kubectl port-forward zeppelin-server-name 8080:80" and navigate to localhost:8080, you should see a working Zeppelin notebook

## Run Native Flink on AKS
1. Start a new paragraph with %flink, then configure mode, flink cluster name, server account and container image, see example here:
   ![image](https://user-images.githubusercontent.com/41244494/138946983-70d62502-7b3e-4139-bd3c-eb2de7fafaef.png)
   
2. Run the paragraph mentioned above, then start any flink jobs, they should be running in k8s mode.   


