package org.deepin.serviceImpl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateFormatUtils;
import org.deepin.config.OssConfig;
import org.deepin.service.IFileService;
import org.springframework.stereotype.Service;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.common.utils.IOUtils;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.GetObjectRequest;
import com.aliyun.oss.model.ObjectMetadata;

@Service
public class FileServiceImpl implements IFileService{
	
  private  OSSClient ossClient;
  
  /***
   * 文件上传
   */
  public String upload(File file, String folder, String name, String contentType, String format) {
    if (StringUtils.isBlank(name)) {
    	name = UUID.randomUUID().toString();
    	String fileExt = FilenameUtils.getExtension(file.getName());
	    if (StringUtils.isNotBlank(fileExt))
	        fileExt = fileExt + "." + fileExt;
	    }
    try{
      return upload(new FileInputStream(file), folder, name, contentType, format);
    }
    catch (FileNotFoundException e)
    {
      throw new RuntimeException("Upload File Error Caused, File Not Found.", e);
    }
  }

  public String upload(File file, String name)
  {
    return upload(file, null, name, null, null);
  }

  public String upload(InputStream in, String name)
  {
    return upload(in, null, name, null, null);
  }

  public String upload(InputStream in, String folder, String name, String contentType, String fileFormat)
  {
    return upload(in, folder, name, contentType, fileFormat, null);
  }
/**
 *文件上传
 */
  public String upload(InputStream in, String folder, String name, String contentType, String fileFormat, String downloadName)
  {
    if (in == null) {
      throw new IllegalArgumentException("Upload Input Stream Must Not Be Null.");
    }
    boolean useFolder = true;
    String basePath = basePath();
    if (StringUtils.isNotBlank(name)) {
      if (name.startsWith(basePath)) {
        name = name.replaceFirst(basePath, "");
        useFolder = false;
      }
    }
    else {
      name = UUID.randomUUID().toString();
    }
    if (useFolder) {
      if (StringUtils.isNotBlank(folder)) {
        if (!folder.endsWith("/")) {
          folder = folder + "/";
        }
        name = folder + name;
      }
      else {
        name = DateFormatUtils.format(new Date(), "yyyyMMdd") + "/" + name;
      }
    }

    ObjectMetadata objMeta = new ObjectMetadata();

    if (StringUtils.isNotBlank(contentType)) {
      objMeta.setContentType(contentType);
    }

    if (StringUtils.isNotBlank(fileFormat)){
      Map<String,String> meta = new HashMap<String,String>();
      meta.put("file-format", fileFormat);
      objMeta.setUserMetadata(meta);
    }

    if (StringUtils.isNotBlank(downloadName)) {
      try {
        downloadName = URLEncoder.encode(downloadName, "UTF-8");
      }
      catch (UnsupportedEncodingException e) {
        throw new RuntimeException("Update File [" + name + "] Encde Download Name [" + downloadName + "] Error Caused.", e);
      }

      String dispo = "attachment; filename=\"" + downloadName + "\"; filename*=utf-8''" + downloadName;

      objMeta.setContentDisposition(dispo);
    }
    try {
      objMeta.setContentLength(in.available());
      this.ossClient.putObject(OssConfig.OSSBUCKET, OssConfig.OSSFOLDER + name, in, objMeta);
    }
    catch (Exception e) {
      throw new RuntimeException("Update File [" + name + "] Error Caused.", e);
    }
    finally
    {
      IOUtils.safeClose(in);
    }
    name = basePath() + name;
    return name;
  }
/**
 * 获取文件
 * return 文件
 */
  public File get(String name)
  {
    File file = new File(FileUtils.getTempDirectory(), UUID.randomUUID() + "_" + name);

    this.ossClient.getObject(new GetObjectRequest(OssConfig.OSSBUCKET, OssConfig.OSSFOLDER+ name.replaceFirst(basePath(), "")), file);

    return file;
  }
/**
 *删除文件
 *name  文件名字
 */
  public void delete(String name)
  {
    if (StringUtils.isNotBlank(name)) {
      this.ossClient.deleteObject(OssConfig.OSSBUCKET, OssConfig.OSSFOLDER + name.replaceFirst(basePath(), ""));
    }
    else
    {
       System.out.println("Delete File, File Name Or URL Is Blank, Ignore.");
    }
  }
/**
 * 总路径
 * return 路径
 */
  public String basePath()
  {
    return "http://" +OssConfig.OSSBUCKET + "." + OssConfig.OSSHOST + "/" +OssConfig.OSSFOLDER;
  }
  /**
   * 获取文件路径
   * folder 文件夹名字
   * name :文件名
   */
  public String getUrl(String folder, String name)
  {
    String url = basePath();
    if (StringUtils.isNotBlank(folder)) {
      url = url + folder;
    }
    return url + "/" + name;
  }
  /***
   * 获取文件路径
   * name  文件名
   */
  public String getUrl(String name)
  {
    return getUrl(null, name);
  }

  public String uploadAvatar(String userId, InputStream ins)
  {
    return uploadImage("avatar", userId, ins);
  }
  /***
   * 上传文件
   * ins:通过文件流
   * return 返回文件访问路径 文件名你通过uuis命名
   */
  public String uploadImage(InputStream ins)
  {
    return uploadImage("image", UUID.randomUUID().toString(), ins);
  }
 /***
 *获取文件类型
 *ins 文件流 
 */
  public String imageType(InputStream ins)
  {
    ImageInputStream iis = null;
    try {
      try {
        iis = ImageIO.createImageInputStream(ins);
      }
      catch (IOException e) {
        System.out.println("Read Image Input Stream [{}] Error Caused.");
        return null;
      }
      Iterator<ImageReader> iter = ImageIO.getImageReaders(iis);
      String format = null;
      ImageReader reader;
      if (iter.hasNext()) {
        reader = (ImageReader)iter.next();
        try {
          format = reader.getFormatName().toLowerCase();
        }
        catch (IOException e) {
          System.out.println("Get Image Format Name Error Caused.");
        }
        reader.dispose();
      }
      else {
    	  System.out.println("Input Stream [{}] Is Not Image Input Stream.");
      }
      return format;
    }
    finally {
      try {
		iis.close();
	} catch (IOException e) {
		System.out.println("iis close failure！");
	}
      IOUtils.safeClose(ins);
    }
  }
  //初始化
  @SuppressWarnings("unused")
  @PostConstruct	
  private void init()
  {
    System.out.println("init OSS OssConfig 初始化");
    this.ossClient = new OSSClient("http://" + OssConfig.OSSHOSTINTERNAL, OssConfig.OSSACCOUNT, OssConfig.OSSPASSWORD);

    if (!this.ossClient.doesBucketExist(OssConfig.OSSBUCKET)) {
       System.out.println("OSS Bucket [{}] Does Not Exist, Try To Create.OssConfig.OSSBUCKET");

      this.ossClient.createBucket(OssConfig.OSSBUCKET);
      this.ossClient.setBucketAcl(OssConfig.OSSBUCKET, CannedAccessControlList.PublicRead);
    }

    if ((StringUtils.isNotBlank(OssConfig.OSSFOLDER)) && (!"/".equals(OssConfig.OSSFOLDER)))
      try {
        /*if (!OosConfig.OSSFOLDER.endsWith("/")) {
        	OosConfig.OSSFOLDER += "/";
        }*/
        this.ossClient.getObjectMetadata(OssConfig.OSSBUCKET, OssConfig.OSSFOLDER);
      }
      catch (OSSException e) {
        if ("NoSuchKey".equals(e.getErrorCode())) {
           System.out.println("OSS Foler [{}] Does Not Exist, Try To Create, OssConfig.OSSFOLDER.");
          InputStream bin = new ByteArrayInputStream(new byte[0]);
          ObjectMetadata objectMeta = new ObjectMetadata();
          objectMeta.setContentLength(0L);
          try {
            this.ossClient.putObject(OssConfig.OSSBUCKET, OssConfig.OSSFOLDER, bin, objectMeta);
          }
          finally
          {
            IOUtils.safeClose(bin);
          }
        }
        else {
          throw e;
        }
      }
  }
/**
 * 上传图片
 * @param folder 图片你的文件夹
 * @param name  图片名字
 * @param ins 图片字节流
 * @return 图片上传后的路径
 */
  private String uploadImage(String folder, String name, InputStream ins)
  {
    byte[] fileBytes = null;
    try {
      fileBytes = IOUtils.readStreamAsByteArray(ins);
    }
    catch (IOException e) {
      throw new IllegalArgumentException("Upload Image Read File Input Stream Error Caused.", e);
    }
    finally
    {
      IOUtils.safeClose(ins);
    }
    String format = imageType(new ByteArrayInputStream(fileBytes));
    if (ArrayUtils.contains(new String[] { "jpg", "bmp", "png", "jpeg" }, format))
    {
      return upload(new ByteArrayInputStream(fileBytes), folder, name, "image/" + format, format);
    }
    throw new IllegalArgumentException("Unsupported Image File Format [" + format + "]");
  }
}
