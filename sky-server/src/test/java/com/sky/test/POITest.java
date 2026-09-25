package com.sky.test;

import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

public class POITest {

    public static void write() throws Exception{

        XSSFWorkbook excel = new XSSFWorkbook();

        XSSFSheet sheet = excel.createSheet("info");

        XSSFRow row = sheet.createRow(1);

        row.createCell(1).setCellValue("Operation");
        row.createCell(2).setCellValue("Operation");


        row = sheet.createRow(2);
        row.createCell(1).setCellValue("Operation");
        row.createCell(2).setCellValue("Operation");

        row = sheet.createRow(3);
        row.createCell(1).setCellValue("Operation");
        row.createCell(2).setCellValue("Operation");


        FileOutputStream out = new FileOutputStream(new File("D:\\info.xlsx"));
        excel.write(out);


        out.close();
        excel.close();
    }


    public static void read() throws Exception{
        InputStream in = new FileInputStream(new File("D:\\info.xlsx"));


        XSSFWorkbook excel = new XSSFWorkbook(in);

        XSSFSheet sheet = excel.getSheetAt(0);


        int lastRowNum = sheet.getLastRowNum();

        for (int i = 1; i <= lastRowNum ; i++) {

            XSSFRow row = sheet.getRow(i);

            String cellValue1 = row.getCell(1).getStringCellValue();
            String cellValue2 = row.getCell(2).getStringCellValue();
            System.out.println(cellValue1 + " " + cellValue2);
        }


        in.close();
        excel.close();
    }

    public static void main(String[] args) throws Exception {
        //write();
        read();
    }
}
