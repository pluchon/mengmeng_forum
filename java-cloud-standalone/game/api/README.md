# forum-game-api

游戏域跨服务契约：DTO / VO / 内部接口。

- 禁止放 `@FeignClient`、Entity、Mapper、Service 实现
- 消费方在本服务内自行声明 Feign 客户端
