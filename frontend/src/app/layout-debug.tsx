import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";

const inter = Inter({ 
  subsets: ["latin"],
  variable: "--font-inter",
});

export const metadata: Metadata = {
  title: "Injector System - Debug",
  description: "Sistema de inyección de llaves criptográficas - Debug Mode",
};

export default function RootLayoutDebug({
  children,
}: {
  children: React.ReactNode;
}) {
  console.log('[LAYOUT_DEBUG] 🔄 Layout renderizado');
  
  return (
    <html lang="es" className="dark">
      <body
        className={`${inter.variable} font-sans antialiased bg-gray-900 text-white min-h-screen`}
      >
        <div className="min-h-screen">
          <nav className="fixed top-0 left-0 right-0 z-50 bg-gray-900 border-b border-gray-700 h-16 flex items-center px-4">
            <span className="text-xl font-bold text-white">DEBUG MODE - Sin Providers</span>
          </nav>
          <main className="pt-16">
            {children}
          </main>
        </div>
      </body>
    </html>
  );
} 