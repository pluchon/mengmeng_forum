# forum-game-api

游戏域跨服务纯契约模块（普通 jar）。

- 仅放接口与 DTO/VO，**禁止** `@FeignClient`
- 消费方在本服务内自行声明 Feign 客户端
- 当前为占位模块，结算/房间等契约后续按域拆分落地
