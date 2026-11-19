package com.gsx.shop.lab.mapper;

import org.apache.ibatis.annotations.Mapper;

/** 只供测试环境的真实数据库操作，SQL 仍集中在 Mapper XML。 */
@Mapper
public interface LabMapper {
    long lockProduct();

    int waitSecond();
}
