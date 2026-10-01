# 第三方依赖

知华科技 · 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2。

本项目自有代码使用根目录非商业源码 LICENSE。以下第三方依赖保留自己的版权和许可，不受本项目自有代码许可替代。未复制或改写其他进销存产品源码。

| 依赖 | 许可入口 |
| --- | --- |
| Spring Boot / Spring Security / Spring Data | [Apache 2.0](https://github.com/spring-projects/spring-boot/blob/main/LICENSE.txt) |
| Hibernate ORM | [Apache 2.0（7.x）](https://hibernate.org/community/license/) |
| Flyway | [Apache 2.0](https://github.com/flyway/flyway/blob/main/LICENSE.txt) |
| MariaDB Connector/J 3.5.10（连接 MySQL） | [LGPL 2.1](https://github.com/mariadb-corporation/mariadb-connector-j/blob/3.5.10/LICENSE) |
| Vue | [MIT](https://github.com/vuejs/core/blob/main/LICENSE) |
| Vite | [MIT](https://github.com/vitejs/vite/blob/main/LICENSE) |
| Lucide icons | [ISC + MIT（Feather 衍生图标）](https://github.com/lucide-icons/lucide/blob/main/LICENSE) |
| Nginx | [BSD-like license](https://nginx.org/LICENSE) |
| MySQL Server | [MySQL licensing](https://www.mysql.com/about/legal/licensing/) |

Maven 依赖树和 package-lock.json 保留精确传递依赖。容器镜像与数据库软件也有各自许可证；分发或商业交付时评估所有适用的版权、告知和其他义务，尤其是 GPL/LGPL 组件及其链接/分发方式。此表不作许可证兼容性的法律保证。

前端运行依赖的原始许可见 `frontend/public/third-party/vue.txt`、`frontend/public/third-party/lucide.txt`，同样随前端静态文件分发至 `/third-party/`。不修改第三方驱动；其原始许可保留在依赖 JAR 内，对应源码见 [MariaDB Connector/J 3.5.10](https://github.com/mariadb-corporation/mariadb-connector-j/tree/3.5.10)。驱动通过独立 Maven 依赖链接，可以替换 `pom.xml` 中驱动依赖后重新构建；本项目自有代码许可不限制第三方库依法享有的修改、替换和调试权利。
