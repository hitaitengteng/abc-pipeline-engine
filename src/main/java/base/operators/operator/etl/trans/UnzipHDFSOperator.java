package base.operators.operator.etl.trans;

import base.operators.example.Attribute;
import base.operators.example.ExampleSet;
import base.operators.example.set.SimpleExampleSet;
import base.operators.example.table.AttributeFactory;
import base.operators.example.table.DataRow;
import base.operators.example.table.DataRowFactory;
import base.operators.example.table.MemoryExampleTable;
import base.operators.operator.Operator;
import base.operators.operator.OperatorDescription;
import base.operators.operator.OperatorException;
import base.operators.operator.UserError;
import base.operators.operator.ports.InputPort;
import base.operators.operator.ports.OutputPort;
import base.operators.parameter.*;
import base.operators.parameter.conditions.EqualStringCondition;
import base.operators.tools.Ontology;
import base.operators.utils.HDFSUtil;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.Path;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static base.operators.utils.HDFSUtil.getFileSystem;

public class UnzipHDFSOperator extends Operator {

    private InputPort exampleSetInput = getInputPorts().createPort("example set");

    private OutputPort exampleSetOutput = getOutputPorts().createPort("example set");

    public UnzipHDFSOperator(OperatorDescription description) {
        super(description);
    }

    private ZipFile zipFile;
    public int afterunzip;
    private String wildcard;
    private String wildcardexclude;
    private String sourcedirectory; // targetdirectory on screen, renamed because of PDI-7761
    private String movetodirectory;
    private boolean addfiletoresult;
    private boolean isfromprevious;
    private boolean adddate;
    private boolean addtime;
    private boolean isSpecifyDateTimeFormat;
    private String date_time_format;
    private boolean rootzip;
    private boolean createfolder;
    private String nr_limit;
    private String wildcardSource;
    private int iffileexist;
    private boolean createMoveToDirectory;

    private boolean addOriginalTimestamp;
    private boolean setOriginalModificationDate;

    private String success_condition;

    private int NrErrors = 0;
    private int NrSuccess = 0;
    boolean successConditionBroken = false;
    boolean successConditionBrokenExit = false;
    int limitFiles = 0;

    private static SimpleDateFormat daf;
    private boolean dateFormatSet = false;

    private static final String IS_FROM_PREVIOUS = "is_from_previous";
    private static final String PARAMETER_ZIP_FILENAME = "parameter_zip_filename";
    private static final String SOURCE_WILDCARD = "source_wildcard";

    private static final String  USE_ZIP_FILE_AS_ROOT_DIRECTIORY = "use_zip_file_as_root_directory";
    private static final String SOURCE_DIRECTORY = "source_directory";
    private static final String  CREATE_FOLDER = "create_folder";
    private static final String WILDCARD_INCLUDE = "wildcard_include";
    private static final String WILDCARD_EXCLUDE = "wildcard_exclude";

    private static final String SET_DATE_TIME_FORMAT = "set_date_time_format";
    private final String[]  date_time_mode = {"file_name_does_not_contain_date_time","file_name_contain_date(yyyyMMdd)","file_name_contain_time(HHmmssSSS)", "file_name_contain_date_and_time"};
    private static final String DATE_TIME_FORMAT = "date_time_format";
    private final String[] dateTimeFormat = {
            "yyyyMMddHHmmss",
            "yyyy/MM/dd",
            "yyyy-MM-dd",
            "yyyyMMdd",
            "MM/dd/yyyy",
            "MM-dd-yyyy",
            "MM/dd/yy",
            "MM-dd-yy",
            "dd/MM/yyyy",
            "dd-MM-yyyy"};
    private static final String ADD_ORIGINAL_TIME_STAMP = "add_original_time_stamp";
    private static final String  SET_ORIGINAL_MODIFICATION_DATE = "set_original_modification_date";


    private static final String  IF_FILE_EXIST = "if_file_exist";
    private final String[] file_exist_operation_en = {"skip","overwrite","give unique name","fail","overwrite if size different","overwrite if size equals","overwrite if zip bigger","overwrite if zip bigger or equal","overwrite if zip smaller","overwrite if zip smaller or equal"};
    private final String[] file_exist_operation_ch = {"跳过","覆盖","唯一名称","失败","如果大小不一致就覆盖","如果大小一致就覆盖","如果压缩的文件大些就覆盖","如果压缩的文件不小于源文件就覆盖","如果压缩文件小些就覆盖","如果压缩的文件不大于源文件就覆盖"};
    private static final String AFTER_UNZIP = "after_unzip";
    public static final String[] afterUnzipCodeEn = {"don't do anything", "delete file","move file"};
    public static final String[] afterUnzipCodeCh = {"什么都不做","删除文件","移动文件"};
    private static final String MOVE_TO_DIRECTORY = "move_to_directory";
    private static final String  CREATE_FOLDER_TO_MOVE = "create_folder_to_move";

    private static final String  ADD_THE_EXTRACTED_FILE_TO_RESULT = "add_the_extracted_file_to_result";
    private static final String  SUCCESS_CONDITION = "success_condition";
    public String SUCCESS_IF_AT_LEAST_X_FILES_UN_ZIPPED = "success when at least";
    public String SUCCESS_IF_ERRORS_LESS = "success if errors less";
    public String SUCCESS_IF_NO_ERRORS = "success if no errors";
    private final String[] success_conditions_en = {SUCCESS_IF_NO_ERRORS, SUCCESS_IF_AT_LEAST_X_FILES_UN_ZIPPED, SUCCESS_IF_ERRORS_LESS};
    private final String[] success_conditions_ch = {"所有工作正常","至少运行成功一定数量","错误数少于"};
    private static final String  NUMBER_LIMIT= "number_limit";

    @Override
    public void doWork() throws OperatorException {
        //isfromprevious = getParameterAsBoolean(IS_FROM_PREVIOUS);
        wildcardSource = getParameterAsString(SOURCE_WILDCARD);

        rootzip = getParameterAsBoolean(USE_ZIP_FILE_AS_ROOT_DIRECTIORY);
        sourcedirectory = getParameterAsString(SOURCE_DIRECTORY);
        if(!sourcedirectory.endsWith("/")){
            sourcedirectory = sourcedirectory+"/";
        }
        //createfolder = getParameterAsBoolean(CREATE_FOLDER);
        wildcard = getParameterAsString(WILDCARD_INCLUDE);
        wildcardexclude = getParameterAsString(WILDCARD_EXCLUDE);

        adddate = getParameterAsInt(SET_DATE_TIME_FORMAT)==1;
        addtime = getParameterAsInt(SET_DATE_TIME_FORMAT)==2;
        isSpecifyDateTimeFormat = getParameterAsInt(SET_DATE_TIME_FORMAT)==3;
        date_time_format = getParameterAsString(DATE_TIME_FORMAT);
        addOriginalTimestamp = getParameterAsBoolean(ADD_ORIGINAL_TIME_STAMP);
        setOriginalModificationDate = getParameterAsBoolean(SET_ORIGINAL_MODIFICATION_DATE);

        iffileexist = getParameterAsInt(IF_FILE_EXIST);
        afterunzip = getParameterAsInt(AFTER_UNZIP);
        movetodirectory = getParameterAsString(MOVE_TO_DIRECTORY);
        //createMoveToDirectory = getParameterAsBoolean(CREATE_FOLDER_TO_MOVE);

        addfiletoresult = getParameterAsBoolean(ADD_THE_EXTRACTED_FILE_TO_RESULT);
        success_condition = getParameterAsString(SUCCESS_CONDITION);
        nr_limit = getParameterAsString(NUMBER_LIMIT);
        limitFiles = getParameterAsInt(NUMBER_LIMIT);

        //构造第一个输出
        int id = 0;
        List<Attribute> attributeList = new ArrayList<>();
        Attribute id_attribute = AttributeFactory.createAttribute("id", Ontology.INTEGER);
        attributeList.add(id_attribute);
        Attribute path_attribute = AttributeFactory.createAttribute("path", Ontology.STRING);
        attributeList.add(path_attribute);
        Attribute size_attribute = AttributeFactory.createAttribute("size", Ontology.NUMERICAL);
        attributeList.add(size_attribute);
        Attribute modify_attribute = AttributeFactory.createAttribute("last_modify_time", Ontology.DATE_TIME);
        attributeList.add(modify_attribute);
        Attribute type_attribute = AttributeFactory.createAttribute("type", Ontology.STRING);
        attributeList.add(type_attribute);
        MemoryExampleTable exampleTable = new MemoryExampleTable(attributeList);

        String zipFileName = getParameterAsString(PARAMETER_ZIP_FILENAME);

        String tempDir = FileUtil.class.getClassLoader().getResource("").getFile()+"temp/";
        try {
            File file = new File(tempDir);
            if(file.exists()){
                file.delete();
            }
            boolean result = file.mkdirs();
            if(result){
                //Files.createDirectories(Paths.get(tempDir));
                FileSystem fs = getFileSystem();
                fs.copyToLocalFile(new Path(zipFileName), new Path(tempDir));
                fs.close();
            }else{
                throw new UserError(this, -1, "创建临时文件失败");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        String tempZipFile = tempDir + zipFileName.substring(zipFileName.lastIndexOf("/") + 1);
        try {
            zipFile =new ZipFile(tempZipFile);
            Enumeration takeentrie = zipFile.getEntries();
            ZipEntry zipEntry = null;
            while (takeentrie.hasMoreElements()) {
                zipEntry = (ZipEntry) takeentrie.nextElement();
                String entryName = zipEntry.getName();
                InputStream in = null;
                FileOutputStream out = null;
                if (zipEntry.isDirectory()) {
                    String name = zipEntry.getName();
                    Pattern patternSource = null;
                    boolean unzip = true;
                    if(wildcardSource!=null){
                        patternSource = Pattern.compile( wildcardSource );
                        if(patternSource!=null){
                            Matcher matcher = patternSource.matcher( name );
                            unzip = matcher.matches();
                            if(unzip){
                                name = name.substring(0, name.length() - 1);
                                File  createDirectory = new File(tempDir + name);
                                createDirectory.mkdirs();
                            }
                        }
                    }
                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException ex) {
                        }
                    }
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }

                } else {
                    String fileName = "";
                    int index = entryName.lastIndexOf("\\");
                    if(index!=-1){
                        fileName = entryName.substring(0, index);
                    }
                    index = entryName.lastIndexOf("/");
                    if(index!=-1){
                        fileName = entryName.substring(0, index);
                    }
                    Pattern patternInclude = null;
                    Pattern patternExclude = null;
                    boolean getIt = true;
                    boolean getItexclude = false;

                    if(wildcard!=null){
                        patternInclude = Pattern.compile( wildcard );
                        if(patternInclude!=null){
                            Matcher matcher = patternInclude.matcher( fileName );
                            getIt = matcher.matches();
                        }
                    }
                    if(wildcardexclude!=null){
                        patternExclude = Pattern.compile( wildcardexclude );
                        if(patternExclude!=null){
                            Matcher matcher = patternExclude.matcher( fileName );
                            getItexclude = matcher.matches();
                        }
                    }

                    if (getIt && !getItexclude) {
                        File createDirectory = new File(tempDir+ fileName);
                        createDirectory.mkdirs();
                    }
                    String unpackFileName = getTargetFilename(zipEntry);
                    File file = new File((tempDir + unpackFileName).substring(0, (tempDir + unpackFileName).lastIndexOf("/")));
                    boolean result = file.mkdirs();
                    //Files.createDirectories(Paths.get((tempDir + unpackFileName).substring(0, (tempDir + unpackFileName).lastIndexOf("/"))));
                    File unpackfile = new File(tempDir + unpackFileName);
                    if(setOriginalModificationDate){
                       unpackfile.setLastModified(zipEntry.getLastModifiedDate().getTime());
                    }
                    in = zipFile.getInputStream(zipEntry);
                    out = new FileOutputStream(unpackfile);
                    int c;
                    byte[] by = new byte[1024];
                    while ((c = in.read(by)) != -1) {
                        out.write(by, 0, c);
                    }
                    out.flush();

                    if (in != null) {
                        try {
                            in.close();
                        } catch (IOException ex) {
                        }
                    }
                    if (out != null) {
                        try {
                            out.close();
                        } catch (IOException ex) {
                            ex.printStackTrace();
                        }
                    }
                    uploadLocalFileToHdfs(sourcedirectory + unpackFileName, new File(unpackFileName));
                    DataRowFactory factory = new DataRowFactory(0, '.');
                    DataRow dataRow = factory.create(attributeList.size());
                    dataRow.set(id_attribute, id);
                    id++;
                    dataRow.set(path_attribute, path_attribute.getMapping().mapString(sourcedirectory + unpackFileName));
                    dataRow.set(size_attribute, new File(tempDir+unpackFileName).length());
                    dataRow.set(modify_attribute, zipEntry.getLastModifiedDate().getTime());
                    dataRow.set(type_attribute, type_attribute.getMapping().mapString(zipEntry.getName().substring(zipEntry.getName().lastIndexOf( '.' )+1)));
                    exampleTable.addDataRow(dataRow);
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        deleteDir(tempDir);
        if(afterunzip==1){
            HDFSUtil.deleteFile(zipFileName);
        }else if(afterunzip==2){
            if(movetodirectory.endsWith("/")){
                movetodirectory = movetodirectory + zipFileName.substring(zipFileName.lastIndexOf("/")+1);
            }
            try {
                HDFSUtil.copyFile(zipFileName, movetodirectory);
            } catch (IOException e) {
                e.printStackTrace();
            }
            HDFSUtil.deleteFile(zipFileName);
        }

        ExampleSet exampleSet1 = new SimpleExampleSet(exampleTable);
        exampleSetOutput.deliver(exampleSet1);
    }

    protected String getTargetFilename( ZipEntry zipEntry) {
        String rawFileName = zipEntry.getName();
        String retval = "";
        // Replace possible environment variables...
        if ( rawFileName != null ) {
            retval = rawFileName;
        }

        if ( !isSpecifyDateTimeFormat && !adddate && !addtime ) {
            return retval;
        }

        int lenstring = retval.length();
        int lastindexOfDot = retval.lastIndexOf( '.' );
        if ( lastindexOfDot == -1 ) {
            lastindexOfDot = lenstring;
        }

        retval = retval.substring( 0, lastindexOfDot );

        if ( daf == null ) {
            daf = new SimpleDateFormat();
        }

        Date timestamp = new Date();
        if ( addOriginalTimestamp ) {
            timestamp = zipEntry.getLastModifiedDate();
        }

        if ( isSpecifyDateTimeFormat && date_time_format!=null) {
            if ( !dateFormatSet ) {
                daf.applyPattern( date_time_format );
            }
            String dt = daf.format( timestamp );
            retval += dt;
        } else {

            if ( adddate ) {
                if ( !dateFormatSet ) {
                    daf.applyPattern( "yyyyMMdd" );
                }
                String d = daf.format( timestamp );
                retval += "_" + d;
            }
            if ( addtime ) {
                if ( !dateFormatSet ) {
                    daf.applyPattern( "HHmmssSSS" );
                }
                String t = daf.format( timestamp );
                retval += "_" + t;
            }
        }

        if ( daf != null ) {
            dateFormatSet = true;
        }

        retval += rawFileName.substring(lastindexOfDot, lenstring );

        return retval;

    }

    // 实现上传文件到hdfs功能
    public void uploadLocalFileToHdfs(String hdfsPath, File file) throws IOException {
        boolean take = takeThisFile(hdfsPath, file);
        FileSystem fileSystem = getFileSystem();
        Path path = new Path(hdfsPath);
        if (fileSystem.exists(path) && !take) {
            return;
        }
        // Create a new file and write data to it.
        FSDataOutputStream out = fileSystem.create(path);
        InputStream in = new BufferedInputStream(new FileInputStream(file));

        byte[] b = new byte[1024];
        int numBytes = 0;
        while ((numBytes = in.read(b)) > 0) {
            out.write(b, 0, numBytes);
        }
        // Close all the file descriptors
        in.close();
        out.close();
        fileSystem.close();
    }

    private boolean takeThisFile( String hdfsDestinationFilePath, File sourceFile ){
        boolean retval = false;
        FileSystem fileSystem = getFileSystem();
        // Check if the file already exists
        Path path = new Path(hdfsDestinationFilePath);
        FSDataOutputStream out = null;
        try {
            out = fileSystem.create(path);
            if ( !fileSystem.exists(path) ) {
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        if ( iffileexist == 0 ) {
            return false;
        }
        if ( iffileexist == 3 ) {
            updateErrors();
            return false;
        }

        if ( iffileexist == 1 ) {
            return true;
        }

        Long entrySize = sourceFile.length();

        Long destinationSize = null;
        try {
            destinationSize = getFileSystem().getFileStatus(path).getLen();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if ( iffileexist == 4 ) {
            if ( entrySize != destinationSize ) {
                return true;
            } else {
                return false;
            }
        }
        if ( iffileexist == 5 ) {
            if ( entrySize == destinationSize ) {
                return true;
            } else {
                return false;
            }
        }
        if ( iffileexist == 6 ) {
            if ( entrySize > destinationSize ) {
                return true;
            } else {
                return false;
            }
        }
        if ( iffileexist == 7 ) {
            if ( entrySize >= destinationSize ) {
                return true;
            } else {
                return false;
            }
        }
        if ( iffileexist == 8 ) {
            if ( entrySize < destinationSize ) {
                return true;
            } else {
                return false;
            }
        }
        if ( iffileexist == 9 ) {
            if ( entrySize <= destinationSize ) {
                return true;
            } else {

                return false;
            }
        }
        if ( iffileexist == 2 ) {
            // Create file with unique name
            return true;
        }

        return retval;
    }

    private void updateErrors() {
        NrErrors++;
    }

    public static void deleteDir(String dirPath) {
        File file = new File(dirPath);
        if(file.isFile()) {
            file.delete();
        }else {
            File[] files = file.listFiles();
            if(files == null) {
                file.delete();
            }else {
                for (int i = 0; i < files.length; i++) {
                    deleteDir(files[i].getAbsolutePath());
                }
                file.delete();
            }
        }
    }

    @Override
    public List<ParameterType> getParameterTypes() {
        List<ParameterType> types = super.getParameterTypes();

//        types.add(new ParameterTypeBoolean(IS_FROM_PREVIOUS,"", false, false));
//        ParameterType type = new ParameterTypeString(PARAMETER_ZIP_FILENAME, "Location of zip file.", false, false);
//        type.registerDependencyCondition(new BooleanParameterCondition(this, IS_FROM_PREVIOUS, false, false));
//        types.add(type);

        types.add(new ParameterTypeString(PARAMETER_ZIP_FILENAME, "Location of zip file.", false, false));
        types.add(new ParameterTypeRegexp(SOURCE_WILDCARD, "Source wildcard.",true, false));

        types.add(new ParameterTypeBoolean(USE_ZIP_FILE_AS_ROOT_DIRECTIORY,"Use compressed file names as root directory names.",false,false));
        types.add(new ParameterTypeString(SOURCE_DIRECTORY,"Unzipped target directory.", false));
        //types.add(new ParameterTypeBoolean(CREATE_FOLDER,"Whether to create folder.",false,false));
        types.add(new ParameterTypeRegexp(WILDCARD_INCLUDE, "Wildcard include.",true, false));
        types.add(new ParameterTypeRegexp(WILDCARD_EXCLUDE, "Wildcard exclude.",true, false));

        types.add(new ParameterTypeCategory(SET_DATE_TIME_FORMAT,"Whether to set the format of date and time.", date_time_mode,0, false));
        ParameterType type = new ParameterTypeCategory(DATE_TIME_FORMAT,"The format of date and time.", dateTimeFormat,0, false);
        type.registerDependencyCondition(new EqualStringCondition(this, SET_DATE_TIME_FORMAT, false,date_time_mode[3]));
        types.add(type);
        type = new ParameterTypeBoolean(ADD_ORIGINAL_TIME_STAMP,"Whether to add the original timestamp.", false);
        type.registerDependencyCondition(new EqualStringCondition(this, SET_DATE_TIME_FORMAT, false,date_time_mode[3]));
        types.add(type);

        types.add(new ParameterTypeBoolean(SET_ORIGINAL_MODIFICATION_DATE, "Whether to add a modification date to the source file.",false, false));

        types.add(new ParameterTypeCategory(IF_FILE_EXIST, "Which operation is selected when the file exists.", file_exist_operation_en,0, false ));

        types.add(new ParameterTypeCategory(AFTER_UNZIP, "Operation of source files after decompression.", afterUnzipCodeEn, 0, false));
        type = new ParameterTypeString(MOVE_TO_DIRECTORY,"Move file to the directory.", true);
        type.registerDependencyCondition(new EqualStringCondition(this, AFTER_UNZIP, false, afterUnzipCodeEn[2]));
        types.add(type);
//        type = new ParameterTypeBoolean(CREATE_FOLDER_TO_MOVE,"Whether to create folder when move resource file.",false,false);
//        type.registerDependencyCondition(new EqualStringCondition(this, AFTER_UNZIP, false, afterUnzipCodeEn[2]));
//        types.add(type);

        types.add(new ParameterTypeBoolean(ADD_THE_EXTRACTED_FILE_TO_RESULT,"Whether to add the extracted file to result.", false,false));
        types.add(new ParameterTypeCategory(SUCCESS_CONDITION, "The condition of success.",success_conditions_en, 0,  false));
        type = new ParameterTypeInt(NUMBER_LIMIT,"The number of limit.",Integer.MIN_VALUE, Integer.MAX_VALUE, 10,false);
        type.registerDependencyCondition(new EqualStringCondition(this, SUCCESS_CONDITION, false, success_conditions_en[1], success_conditions_en[2]));
        types.add(type);

        return types;
    }

}
