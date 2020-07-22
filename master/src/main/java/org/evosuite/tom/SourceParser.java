package org.evosuite.tom;

import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SourceParser {

    private ASTParser parser;
    private static SourceParser sourceParser = new SourceParser();

    public static SourceParser getInstance(){
        return sourceParser;
    }

    public SourceParser(){
        parser = ASTParser.newParser(AST.JLS8);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
    }

    public List<Integer> getCommentLines(String className, String sourceCode) {
        parser.setSource(sourceCode.toCharArray());

        List<Integer> commentLines = new ArrayList<>();
        final CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        for (Comment comment : (List<Comment>) cu.getCommentList()) {
            int start = comment.getStartPosition();
            int end = start+comment.getLength();
            int startLine = cu.getLineNumber(start);
            int endLine = cu.getLineNumber(end);
            for(int i = startLine;i<=endLine;i++){
                commentLines.add(i);
            }
        }
        return commentLines;
    }

    public Map<String,List<String>> getEntities(String className, String sourceCode){
        parser.setSource(sourceCode.toCharArray());

        Map<String,List<String>> entityLines = new HashMap<>();
        final CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        List<Integer> commentLines = getCommentLines(className, sourceCode);
        String[] sourceLines =  sourceCode.split("\n");
		final String packageName = cu.getPackage()!=null ? cu.getPackage().getName().toString()+".":"";

        cu.accept(new ASTVisitor(){

			private String getQualifiedName(ASTNode node){
				List<String> parentsNames = new ArrayList<>();
				ASTNode parent = node.getParent();
				do{
					if(parent instanceof TypeDeclaration){
						parentsNames.add(((TypeDeclaration)parent).getName().toString());
					}
					if(parent instanceof MethodDeclaration){
						parentsNames.add(((MethodDeclaration)parent).getName().toString());
					}
					if(parent instanceof AnonymousClassDeclaration){
						parentsNames.add("$i");
					}
					parent = parent.getParent();
				}while(parent!=null);
				String qualifiedName = "";
				for(int i = parentsNames.size()-1;i>=0;i--){
					qualifiedName += (parentsNames.get(i)+".");
				}
				return qualifiedName;
			}
						
            @Override
            public boolean visit(MethodDeclaration node){
                int start = node.getStartPosition();
                String fullName = packageName+getQualifiedName(node)+node.getName().toString();
                List<String> lines = new ArrayList<>();

                for(int i=cu.getLineNumber(start);i<=cu.getLineNumber(start+node.getLength());i++){
                    if(!commentLines.contains(i)){
                        lines.add(sourceLines[i-1].trim());
                    }
                }
                fullName+="(";
				for(Object parameter: node.parameters()){
					if(parameter instanceof SingleVariableDeclaration){
						fullName+=((((SingleVariableDeclaration)parameter).getType().toString())+",");
					}
				}
				if(node.parameters().size()>0){
					fullName = fullName.substring(0,fullName.length()-1);
				}
                fullName+=")";

                entityLines.put(fullName,lines);
                return true;
            }
            @Override
            public boolean visit(VariableDeclarationFragment node){
                if(node.getParent() instanceof FieldDeclaration){
					String fullName = packageName+getQualifiedName(node.getParent())+node.getName().getIdentifier();
                    int start = node.getStartPosition();
                    List<String> lines = new ArrayList<>();
                    for(int i = cu.getLineNumber(start);i<=cu.getLineNumber(start+node.getLength());i++){
                        if(!commentLines.contains(i)){
                            lines.add(sourceLines[i-1].trim());
                        }
                    }
                    entityLines.put(fullName,lines);
                }
                return true;
            }
        });

        return entityLines;
    }
}
