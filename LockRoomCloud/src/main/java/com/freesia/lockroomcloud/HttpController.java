package com.freesia.lockroomcloud;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
public class HttpController {
    private static final String path = "/home/admin/LockRoom/";



    @RequestMapping(value = "/upload",method = RequestMethod.POST,consumes = "multipart/form-data")
    @ResponseBody
    public String UploadController(HttpServletRequest request){
        MultipartHttpServletRequest multipartHttpServletRequest = (MultipartHttpServletRequest) request;
        MultipartFile multipartFile = multipartHttpServletRequest.getFile("file");

        String uuid = UUID.randomUUID().toString().replace("-","");

        String filename = multipartHttpServletRequest.getParameter("filename");
        int times = Integer.parseInt(multipartHttpServletRequest.getParameter("times"));
        boolean isPermanent = multipartHttpServletRequest.getParameter("isPermanent").equals("true");
        String key = multipartHttpServletRequest.getParameter("key");
        boolean isDiary = multipartHttpServletRequest.getParameter("isDiary").equals("true");

        String md5 = DigestUtils.md5Hex(uuid + filename + times + isPermanent + key + isDiary);

        ApplicationContext ac = new AnnotationConfigApplicationContext(AppConfig.class);
        DataSource d = (DataSource) ac.getBean("dataSource");
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(d);
        jdbcTemplate.update("INSERT INTO shareinfo VALUES(?,?,?,?,?,?,?);",
                uuid, filename, times, isPermanent?1:0, key, isDiary?1:0, md5);


        try {
            File file = new File(path+uuid);
            multipartFile.transferTo(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return md5;
    }

    @RequestMapping(value = "/download",method = RequestMethod.POST,consumes = "application/json",produces = "multipart/form-data")
    @ResponseBody
    public ResponseEntity<MultiValueMap<String, Object>> DownloadController(@RequestBody Map<String, Object> paramsMap){
        MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
        String md5 = (String) paramsMap.get("md5");
        ApplicationContext ac = new AnnotationConfigApplicationContext(AppConfig.class);
        DataSource d = (DataSource) ac.getBean("dataSource");
        JdbcTemplate jdbcTemplate = new JdbcTemplate();
        jdbcTemplate.setDataSource(d);
        List<Map<String, Object>> res = jdbcTemplate.queryForList("SELECT * FROM shareinfo WHERE md5 = ?", md5);
        if(res.size() == 1){
            Map<String, Object> map = res.get(0);
            String uuid = (String) map.get("uuid");
            String filename = (String) map.get("filename");
            int times = (int) map.get("times");
            boolean isPermanent = ((int) map.get("isPermanent")) == 1;
            String aeskey = (String) map.get("aeskey");
            boolean isDiary = ((int) map.get("isDiary")) == 1;

            if(times > 0) {
                --times;
                formData.add("file", new FileSystemResource(path+uuid));
                formData.add("filename", filename);
                formData.add("aeskey", aeskey);
                formData.add("isDiary", isDiary? "true": "false");

                //更新数据库
                jdbcTemplate.update("UPDATE shareinfo SET times = ? WHERE uuid = ?;", times, uuid);
            }
            else {
                formData.add("isDiary", "times_error");
                if(!isPermanent){
                    File file = new File(path+uuid);
                    if(file.exists()){
                        file.delete();
                    }
                    //更新数据库
                    jdbcTemplate.update("DELETE FROM shareinfo WHERE uuid = ?;", uuid);
                }
            }
        }
        else{
            //Error
            formData.add("isDiary", "error");
        }

        return new ResponseEntity<>(formData, HttpStatus.OK);
    }
}
