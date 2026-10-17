package org.xhy.infrastructure.repository.github;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GitHubConnectionMapper {
    @Select("SELECT user_id, github_user_id, github_login, encrypted_token, repositories_json "
            + "FROM github_connections WHERE user_id = #{userId}")
    @Results({@Result(column = "user_id", property = "userId"),
            @Result(column = "github_user_id", property = "githubUserId"),
            @Result(column = "github_login", property = "githubLogin"),
            @Result(column = "encrypted_token", property = "encryptedToken"),
            @Result(column = "repositories_json", property = "repositoriesJson")})
    GitHubConnectionRow findByUserId(@Param("userId") String userId);

    @Select("SELECT user_id, github_user_id, github_login, encrypted_token, repositories_json "
            + "FROM github_connections WHERE user_id = #{userId} FOR UPDATE")
    @Results({@Result(column = "user_id", property = "userId"),
            @Result(column = "github_user_id", property = "githubUserId"),
            @Result(column = "github_login", property = "githubLogin"),
            @Result(column = "encrypted_token", property = "encryptedToken"),
            @Result(column = "repositories_json", property = "repositoriesJson")})
    GitHubConnectionRow findByUserIdForUpdate(@Param("userId") String userId);

    @Insert("INSERT INTO github_connections (user_id, github_user_id, github_login, encrypted_token, "
            + "repositories_json) VALUES (#{userId}, #{githubUserId}, #{githubLogin}, #{encryptedToken}, "
            + "#{repositoriesJson}) ON CONFLICT (user_id) DO UPDATE SET github_user_id = EXCLUDED.github_user_id, "
            + "github_login = EXCLUDED.github_login, encrypted_token = EXCLUDED.encrypted_token, "
            + "repositories_json = EXCLUDED.repositories_json, updated_at = CURRENT_TIMESTAMP")
    void upsert(GitHubConnectionRow row);

    @Delete("DELETE FROM github_connections WHERE user_id = #{userId}")
    void deleteByUserId(@Param("userId") String userId);
}
